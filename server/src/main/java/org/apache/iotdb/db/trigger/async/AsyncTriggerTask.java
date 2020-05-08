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

package org.apache.iotdb.db.trigger.async;

import org.apache.iotdb.db.trigger.definition.AsyncTrigger;
import org.apache.iotdb.db.trigger.definition.AsyncTriggerRejectionPolicy;
import org.apache.iotdb.db.trigger.definition.HookID;

public class AsyncTriggerTask {

  private final AsyncTrigger trigger;
  private final HookID hookID;

  private final long timestamp;
  private final Object value;

  private final long[] timestamps;
  private final Object[] values;

  public AsyncTriggerTask(AsyncTrigger trigger, HookID hookID, long timestamp, Object value,
      long[] timestamps, Object[] values) {
    this.trigger = trigger;
    this.hookID = hookID;
    this.timestamp = timestamp;
    this.value = value;
    this.timestamps = timestamps;
    this.values = values;
  }

  public AsyncTrigger getTrigger() {
    return trigger;
  }

  public HookID getHookID() {
    return hookID;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public Object getValue() {
    return value;
  }

  public String getStringValue() {
    return (String) value;
  }

  public Integer getIntegerValue() {
    return (Integer) value;
  }

  public Long getLongValue() {
    return (Long) value;
  }

  public Boolean getBooleanValue() {
    return (Boolean) value;
  }

  public Float getFloatValue() {
    return (Float) value;
  }

  public Double getDoubleValue(String name) {
    return (Double) value;
  }

  public long[] getTimestamps() {
    return timestamps;
  }

  public Object[] getValues() {
    return values;
  }

  public String[] getStringValues() {
    return (String[]) values;
  }

  public Integer[] getIntegerValues() {
    return (Integer[]) values;
  }

  public Long[] getLongValues() {
    return (Long[]) values;
  }

  public Boolean[] getBooleanValues() {
    return (Boolean[]) values;
  }

  public Float[] getFloatValues() {
    return (Float[]) values;
  }

  public Double[] getDoubleValues(String name) {
    return (Double[]) values;
  }

  public AsyncTriggerRejectionPolicy getRejectionPolicy() {
    return trigger.getRejectionPolicy(this);
  }
}
