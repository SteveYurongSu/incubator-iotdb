/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.trigger.manager;

import static org.apache.iotdb.db.trigger.define.HookID.ON_BATCH_AFTER_INSERT;
import static org.apache.iotdb.db.trigger.define.HookID.ON_BATCH_BEFORE_INSERT;
import static org.apache.iotdb.db.trigger.define.HookID.ON_DATA_POINT_AFTER_DELETE;
import static org.apache.iotdb.db.trigger.define.HookID.ON_DATA_POINT_AFTER_INSERT;
import static org.apache.iotdb.db.trigger.define.HookID.ON_DATA_POINT_BEFORE_DELETE;
import static org.apache.iotdb.db.trigger.define.HookID.ON_DATA_POINT_BEFORE_INSERT;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.iotdb.db.exception.StartupException;
import org.apache.iotdb.db.exception.metadata.MetadataException;
import org.apache.iotdb.db.exception.metadata.PathNotExistException;
import org.apache.iotdb.db.exception.trigger.TriggerException;
import org.apache.iotdb.db.exception.trigger.TriggerInstanceLoadException;
import org.apache.iotdb.db.exception.trigger.TriggerManagementException;
import org.apache.iotdb.db.metadata.MManager;
import org.apache.iotdb.db.metadata.mnode.MNode;
import org.apache.iotdb.db.service.IService;
import org.apache.iotdb.db.service.ServiceType;
import org.apache.iotdb.db.trigger.async.AsyncTriggerJob;
import org.apache.iotdb.db.trigger.async.AsyncTriggerScheduler;
import org.apache.iotdb.db.trigger.define.AsyncTrigger;
import org.apache.iotdb.db.trigger.define.SyncTrigger;
import org.apache.iotdb.db.trigger.define.SyncTriggerExecutionResult;
import org.apache.iotdb.db.trigger.define.Trigger;
import org.apache.iotdb.db.trigger.define.TriggerParameterConfiguration;
import org.apache.iotdb.db.trigger.storage.TriggerStorageService;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.read.common.Path;
import org.apache.iotdb.tsfile.write.record.datapoint.DataPoint;
import org.apache.iotdb.tsfile.write.record.datapoint.LongDataPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TriggerManager implements IService {

  private static final Logger logger = LoggerFactory.getLogger(TriggerManager.class);

  private final ConcurrentMap<String, Trigger> idToTriggers;
  private final ConcurrentMap<String, Trigger> pathToSyncTriggers;
  private final ConcurrentMap<String, Trigger> pathToAsyncTriggers;

  private TriggerManager() {
    idToTriggers = new ConcurrentHashMap<>();
    pathToSyncTriggers = new ConcurrentHashMap<>();
    pathToAsyncTriggers = new ConcurrentHashMap<>();
  }

  @Override
  public void start() throws StartupException {
    try {
      // todo: init pool here?
      initMapsAndStartTriggers(TriggerStorageService.getInstance().recoveryAllTriggers());
      logger.info("TriggerManager service started.");
    } catch (TriggerException e) {
      throw new StartupException(String.format("Failed to start TriggerManager service, because %s",
          e.getMessage()));
    }
  }

  @Override
  public void stop() {
    stopTriggers();
    logger.info("TriggerManager service stopped.");
  }

  @Override
  public ServiceType getID() {
    return ServiceType.TRIGGER_MANAGER_SERVICE;
  }

  /**
   * DOCUMENT ME! stringValues in insertPlan may be modified here.
   */
  public SyncTriggerExecutionResult fireBeforeInsert(Path[] paths, long timestamp,
      TSDataType[] tsDataTypes, String[] stringValues) {
    // fire sync triggers
    SyncTriggerExecutionResult result = SyncTriggerExecutionResult.DATA_POINT_NOT_CHANGED;
    for (int i = 0; i < paths.length; ++i) {
      SyncTrigger syncTrigger = (SyncTrigger) pathToSyncTriggers.get(paths[i].getFullPath());
      if (syncTrigger == null || !syncTrigger.isActive() || !ON_DATA_POINT_BEFORE_INSERT
          .isEnabled(syncTrigger.getEnabledHooks())) {
        continue;
      }
      DataPoint dataPoint = DataPoint
          .getDataPoint(tsDataTypes[i], paths[i].getMeasurement(), stringValues[i]);
      SyncTriggerExecutionResult executionResult = syncTrigger
          .onDataPointBeforeInsert(timestamp, dataPoint);
      if (executionResult.equals(SyncTriggerExecutionResult.SKIP)) {
        result = SyncTriggerExecutionResult.SKIP;
      } else if (executionResult.equals(SyncTriggerExecutionResult.DATA_POINT_CHANGED)) {
        stringValues[i] = dataPoint.getValue().toString();
        result = result.equals(SyncTriggerExecutionResult.SKIP) ?
            SyncTriggerExecutionResult.SKIP : SyncTriggerExecutionResult.DATA_POINT_CHANGED;
      }
    }

    // fire async triggers
    for (int i = 0; i < paths.length; ++i) {
      AsyncTrigger asyncTrigger = (AsyncTrigger) pathToAsyncTriggers.get(paths[i].getFullPath());
      if (asyncTrigger == null || !asyncTrigger.isActive() || !ON_DATA_POINT_BEFORE_INSERT
          .isEnabled(asyncTrigger.getEnabledHooks())) {
        continue;
      }
      Object value = DataPoint
          .getDataPoint(tsDataTypes[i], paths[i].getMeasurement(), stringValues[i]).getValue();
      AsyncTriggerScheduler.getInstance().submit(
          new AsyncTriggerJob(asyncTrigger, ON_DATA_POINT_BEFORE_INSERT, timestamp, value, null,
              null));
    }

    return result;
  }

  public void fireAfterInsert(Path[] paths, long timestamp, TSDataType[] tsDataTypes,
      String[] stringValues) {
    // fire sync triggers
    for (int i = 0; i < paths.length; ++i) {
      SyncTrigger syncTrigger = (SyncTrigger) pathToSyncTriggers.get(paths[i].getFullPath());
      if (syncTrigger == null || !syncTrigger.isActive() || !ON_DATA_POINT_AFTER_INSERT
          .isEnabled(syncTrigger.getEnabledHooks())) {
        continue;
      }
      DataPoint dataPoint = DataPoint
          .getDataPoint(tsDataTypes[i], paths[i].getMeasurement(), stringValues[i]);
      syncTrigger.onDataPointAfterInsert(timestamp, dataPoint.getValue());
    }

    // fire async triggers
    for (int i = 0; i < paths.length; ++i) {
      AsyncTrigger asyncTrigger = (AsyncTrigger) pathToAsyncTriggers.get(paths[i].getFullPath());
      if (asyncTrigger == null || !asyncTrigger.isActive() || !ON_DATA_POINT_AFTER_INSERT
          .isEnabled(asyncTrigger.getEnabledHooks())) {
        continue;
      }
      Object value = DataPoint
          .getDataPoint(tsDataTypes[i], paths[i].getMeasurement(), stringValues[i]).getValue();
      AsyncTriggerScheduler.getInstance().submit(
          new AsyncTriggerJob(asyncTrigger, ON_DATA_POINT_AFTER_INSERT, timestamp, value, null,
              null));
    }
  }

  public SyncTriggerExecutionResult fireBeforeDelete(Path path, LongDataPoint timestamp) {
    // fire sync trigger
    SyncTriggerExecutionResult result = SyncTriggerExecutionResult.DATA_POINT_NOT_CHANGED;
    SyncTrigger syncTrigger = (SyncTrigger) pathToSyncTriggers.get(path.getFullPath());
    if (syncTrigger != null && syncTrigger.isActive() && ON_DATA_POINT_BEFORE_DELETE
        .isEnabled(syncTrigger.getEnabledHooks())) {
      result = syncTrigger.onDataPointBeforeDelete(timestamp);
    }

    // fire async trigger
    AsyncTrigger asyncTrigger = (AsyncTrigger) pathToAsyncTriggers.get(path.getFullPath());
    if (asyncTrigger != null && asyncTrigger.isActive() && ON_DATA_POINT_BEFORE_DELETE
        .isEnabled(asyncTrigger.getEnabledHooks())) {
      AsyncTriggerScheduler.getInstance().submit(
          new AsyncTriggerJob(asyncTrigger, ON_DATA_POINT_BEFORE_DELETE,
              (Long) timestamp.getValue(), null, null, null));
    }

    return result;
  }

  public void fireAfterDelete(Path path, long timestamp) {
    // fire sync trigger
    SyncTrigger syncTrigger = (SyncTrigger) pathToSyncTriggers.get(path.getFullPath());
    if (syncTrigger != null && syncTrigger.isActive() && ON_DATA_POINT_AFTER_DELETE
        .isEnabled(syncTrigger.getEnabledHooks())) {
      syncTrigger.onDataPointAfterDelete(timestamp);
    }

    AsyncTrigger asyncTrigger = (AsyncTrigger) pathToAsyncTriggers.get(path.getFullPath());
    if (asyncTrigger != null && asyncTrigger.isActive() && ON_DATA_POINT_AFTER_DELETE
        .isEnabled(asyncTrigger.getEnabledHooks())) {
      AsyncTriggerScheduler.getInstance().submit(
          new AsyncTriggerJob(asyncTrigger, ON_DATA_POINT_AFTER_DELETE, timestamp, null, null,
              null));
    }
  }

  //! times should be sorted.
  public SyncTriggerExecutionResult fireBeforeBatchInsert(Path[] paths, long[] timestamps,
      Object[] values) {
    // fire sync triggers
    SyncTriggerExecutionResult result = SyncTriggerExecutionResult.DATA_POINT_NOT_CHANGED;
    for (int i = 0; i < paths.length; ++i) {
      SyncTrigger syncTrigger = (SyncTrigger) pathToSyncTriggers.get(paths[i].getFullPath());
      if (syncTrigger == null || !syncTrigger.isActive() || !ON_BATCH_BEFORE_INSERT
          .isEnabled(syncTrigger.getEnabledHooks())) {
        continue;
      }
      SyncTriggerExecutionResult executionResult = syncTrigger
          .onBatchBeforeInsert(timestamps, (Object[]) values[i]);
      if (executionResult.equals(SyncTriggerExecutionResult.SKIP)) {
        result = SyncTriggerExecutionResult.SKIP;
      } else if (executionResult.equals(SyncTriggerExecutionResult.DATA_POINT_CHANGED)) {
        result = result.equals(SyncTriggerExecutionResult.SKIP) ?
            SyncTriggerExecutionResult.SKIP : SyncTriggerExecutionResult.DATA_POINT_CHANGED;
      }
    }

    // fire async triggers
    for (int i = 0; i < paths.length; ++i) {
      AsyncTrigger asyncTrigger = (AsyncTrigger) pathToAsyncTriggers.get(paths[i].getFullPath());
      if (asyncTrigger == null || !asyncTrigger.isActive() || !ON_BATCH_BEFORE_INSERT
          .isEnabled(asyncTrigger.getEnabledHooks())) {
        continue;
      }
      AsyncTriggerScheduler.getInstance().submit(
          new AsyncTriggerJob(asyncTrigger, ON_BATCH_BEFORE_INSERT, -1, null, timestamps,
              (Object[]) values[i]));
    }

    return result;
  }

  // modify timestamps in after methods may cause undefine behavior.
  public void fireAfterBatchInsert(Path[] paths, long[] timestamps, Object[] values) {
    // fire sync triggers
    for (int i = 0; i < paths.length; ++i) {
      SyncTrigger syncTrigger = (SyncTrigger) pathToSyncTriggers.get(paths[i].getFullPath());
      if (syncTrigger == null || !syncTrigger.isActive() || !ON_BATCH_AFTER_INSERT
          .isEnabled(syncTrigger.getEnabledHooks())) {
        continue;
      }
      syncTrigger.onBatchAfterInsert(timestamps, (Object[]) values[i]);
    }

    // fire async triggers
    for (int i = 0; i < paths.length; ++i) {
      AsyncTrigger asyncTrigger = (AsyncTrigger) pathToAsyncTriggers.get(paths[i].getFullPath());
      if (asyncTrigger == null || !asyncTrigger.isActive() || !ON_BATCH_AFTER_INSERT
          .isEnabled(asyncTrigger.getEnabledHooks())) {
        continue;
      }
      AsyncTriggerScheduler.getInstance().submit(
          new AsyncTriggerJob(asyncTrigger, ON_BATCH_AFTER_INSERT, -1, null, timestamps,
              (Object[]) values[i]));
    }
  }

  public void create(String className, String path, String id, int enabledHooks,
      TriggerParameterConfiguration[] parameterConfigurations)
      throws TriggerInstanceLoadException, TriggerManagementException, MetadataException {
    checkPath(path);
    Trigger trigger = TriggerStorageService.getInstance()
        .createTrigger(className, path, id, enabledHooks, parameterConfigurations);
    trigger.beforeStart();
    idToTriggers.put(trigger.getId(), trigger);
    if (trigger.isSynced()) {
      pathToSyncTriggers.put(trigger.getPath(), trigger);
    } else {
      pathToAsyncTriggers.put(trigger.getPath(), trigger);
    }
  }

  public void start(String id) throws TriggerManagementException {
    Trigger trigger = idToTriggers.get(id);
    if (trigger == null) {
      throw new TriggerManagementException(String
          .format("Could not start Trigger(ID: %s), because the trigger does not exist.", id));
    }
    if (trigger.isActive()) {
      throw new TriggerManagementException(String
          .format("Trigger(ID: %s) has already been started.", id));
    }
    trigger.beforeStart();
    trigger.markAsActive();
    TriggerStorageService.getInstance().updateTrigger(trigger);
  }

  public void stop(String id) throws TriggerManagementException {
    Trigger trigger = idToTriggers.get(id);
    if (trigger == null) {
      throw new TriggerManagementException(String
          .format("Could not stop trigger(ID: %s), because the trigger does not exist.", id));
    }
    if (!trigger.isActive()) {
      throw new TriggerManagementException(String
          .format("Trigger(ID: %s) has already been stopped.", id));
    }
    trigger.markAsInactive();
    TriggerStorageService.getInstance().updateTrigger(trigger);
    trigger.afterStop();
  }

  public void removeById(String id) throws TriggerManagementException {
    Trigger trigger = idToTriggers.get(id);
    if (trigger == null) {
      throw new TriggerManagementException(String
          .format("Could not remove Trigger(ID: %s), because the trigger does not exist.", id));
    }
    if (trigger.isActive()) {
      trigger.markAsInactive();
      trigger.afterStop();
    }
    TriggerStorageService.getInstance().removeTrigger(trigger);
    idToTriggers.remove(id);
    if (trigger.isSynced()) {
      pathToSyncTriggers.remove(trigger.getPath());
    } else {
      pathToAsyncTriggers.remove(trigger.getPath());
    }
  }

  public void removeByPath(String path) throws TriggerManagementException {
    removeByPath(path, pathToSyncTriggers);
    removeByPath(path, pathToAsyncTriggers);
  }

  private void removeByPath(String path, ConcurrentMap<String, Trigger> map)
      throws TriggerManagementException {
    Trigger trigger = map.get(path);
    if (trigger == null) {
      logger.info("Could not remove {} trigger(path: {}), because the trigger does not exist.",
          map == pathToSyncTriggers ? "sync" : "async", path);
      return;
    }
    if (trigger.isActive()) {
      trigger.markAsInactive();
      trigger.afterStop();
    }
    TriggerStorageService.getInstance().removeTrigger(trigger);
    idToTriggers.remove(trigger.getId());
    map.remove(trigger.getPath());
    logger.info("{} trigger(path: {}) has been removed successfully.",
        map == pathToSyncTriggers ? "Sync" : "Async", path);
  }

  private void initMapsAndStartTriggers(List<Trigger> triggers) {
    for (Trigger trigger : triggers) {
      if (trigger.isActive()) {
        trigger.beforeStart();
      }
      idToTriggers.put(trigger.getId(), trigger);
      if (trigger.isSynced()) {
        pathToSyncTriggers.put(trigger.getPath(), trigger);
      } else {
        pathToAsyncTriggers.put(trigger.getPath(), trigger);
      }
    }
  }

  private void stopTriggers() {
    for (Trigger trigger : idToTriggers.values()) {
      if (trigger.isActive()) {
        trigger.afterStop();
      }
    }
  }

  private void checkPath(String pathString) throws MetadataException {
    Path path = new Path(pathString);
    MNode node = MManager.getInstance().getDeviceNodeWithAutoCreateStorageGroup(path.getDevice());
    if (!node.hasChild(path.getMeasurement())) {
      throw new PathNotExistException(path.getFullPath());
    }
  }

  public static TriggerManager getInstance() {
    return InstanceHolder.INSTANCE;
  }

  private static class InstanceHolder {

    private InstanceHolder() {
    }

    private static final TriggerManager INSTANCE = new TriggerManager();
  }
}