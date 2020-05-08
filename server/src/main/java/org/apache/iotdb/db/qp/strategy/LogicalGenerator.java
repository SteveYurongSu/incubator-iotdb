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
package org.apache.iotdb.db.qp.strategy;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.exception.runtime.SQLParserException;
import org.apache.iotdb.db.qp.constant.DatetimeUtils;
import org.apache.iotdb.db.qp.constant.SQLConstant;
import org.apache.iotdb.db.qp.logical.RootOperator;
import org.apache.iotdb.db.qp.logical.crud.BasicFunctionOperator;
import org.apache.iotdb.db.qp.logical.crud.DeleteDataOperator;
import org.apache.iotdb.db.qp.logical.crud.FilterOperator;
import org.apache.iotdb.db.qp.logical.crud.FromOperator;
import org.apache.iotdb.db.qp.logical.crud.InOperator;
import org.apache.iotdb.db.qp.logical.crud.InsertOperator;
import org.apache.iotdb.db.qp.logical.crud.QueryOperator;
import org.apache.iotdb.db.qp.logical.crud.SelectOperator;
import org.apache.iotdb.db.qp.logical.crud.UpdateOperator;
import org.apache.iotdb.db.qp.logical.sys.AlterTimeSeriesOperator;
import org.apache.iotdb.db.qp.logical.sys.AlterTimeSeriesOperator.AlterType;
import org.apache.iotdb.db.qp.logical.sys.AuthorOperator;
import org.apache.iotdb.db.qp.logical.sys.AuthorOperator.AuthorType;
import org.apache.iotdb.db.qp.logical.sys.ClearCacheOperator;
import org.apache.iotdb.db.qp.logical.sys.CountOperator;
import org.apache.iotdb.db.qp.logical.sys.CreateTimeSeriesOperator;
import org.apache.iotdb.db.qp.logical.sys.CreateTriggerOperator;
import org.apache.iotdb.db.qp.logical.sys.DataAuthOperator;
import org.apache.iotdb.db.qp.logical.sys.DeleteStorageGroupOperator;
import org.apache.iotdb.db.qp.logical.sys.DeleteTimeSeriesOperator;
import org.apache.iotdb.db.qp.logical.sys.DropTriggerOperator;
import org.apache.iotdb.db.qp.logical.sys.FlushOperator;
import org.apache.iotdb.db.qp.logical.sys.LoadConfigurationOperator;
import org.apache.iotdb.db.qp.logical.sys.LoadDataOperator;
import org.apache.iotdb.db.qp.logical.sys.LoadFilesOperator;
import org.apache.iotdb.db.qp.logical.sys.MergeOperator;
import org.apache.iotdb.db.qp.logical.sys.MoveFileOperator;
import org.apache.iotdb.db.qp.logical.sys.RemoveFileOperator;
import org.apache.iotdb.db.qp.logical.sys.SetStorageGroupOperator;
import org.apache.iotdb.db.qp.logical.sys.SetTTLOperator;
import org.apache.iotdb.db.qp.logical.sys.ShowChildPathsOperator;
import org.apache.iotdb.db.qp.logical.sys.ShowDevicesOperator;
import org.apache.iotdb.db.qp.logical.sys.ShowOperator;
import org.apache.iotdb.db.qp.logical.sys.ShowTTLOperator;
import org.apache.iotdb.db.qp.logical.sys.ShowTimeSeriesOperator;
import org.apache.iotdb.db.qp.logical.sys.StartTriggerOperator;
import org.apache.iotdb.db.qp.logical.sys.StopTriggerOperator;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.AliasContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.AlignByDeviceClauseContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.AlterUserContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.AndExpressionContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.AttributeClauseContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.AttributeClausesContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.ConstantContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.CountNodesContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.CountTimeseriesContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.CreateRoleContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.CreateTimeseriesContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.CreateUserContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.DateExpressionContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.DeleteStatementContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.DeleteStorageGroupContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.DeleteTimeseriesContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.DropRoleContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.DropUserContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.FillClauseContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.FlushContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.FromClauseContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.FullMergeContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.FullPathContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.FunctionCallContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.FunctionElementContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.GrantRoleContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.GrantRoleToUserContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.GrantUserContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.GrantWatermarkEmbeddingContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.GroupByClauseContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.InClauseContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.InsertColumnSpecContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.InsertStatementContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.InsertValuesSpecContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.LastClauseContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.LimitClauseContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.ListAllRoleOfUserContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.ListAllUserOfRoleContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.ListPrivilegesRoleContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.ListPrivilegesUserContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.ListRoleContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.ListRolePrivilegesContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.ListUserContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.ListUserPrivilegesContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.LoadConfigurationStatementContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.LoadFilesContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.LoadStatementContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.MergeContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.MoveFileContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.NodeNameContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.NodeNameWithoutStarContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.OffsetClauseContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.OrExpressionContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.PredicateContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.PrefixPathContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.PrivilegesContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.PropertyContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.PropertyValueContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.RemoveFileContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.RevokeRoleContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.RevokeRoleFromUserContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.RevokeUserContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.RevokeWatermarkEmbeddingContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.RootOrIdContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.SelectConstElementContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.SelectElementContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.SelectStatementContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.SetColContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.SetStorageGroupContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.SetTTLStatementContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.ShowAllTTLStatementContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.ShowChildPathsContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.ShowDevicesContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.ShowStorageGroupContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.ShowTTLStatementContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.ShowTimeseriesContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.ShowVersionContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.ShowWhereClauseContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.SlimitClauseContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.SoffsetClauseContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.SuffixPathContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.TagClauseContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.TimeIntervalContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.TypeClauseContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.UnsetTTLStatementContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.UpdateStatementContext;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.WhereClauseContext;
import org.apache.iotdb.db.query.executor.fill.IFill;
import org.apache.iotdb.db.query.executor.fill.LinearFill;
import org.apache.iotdb.db.query.executor.fill.PreviousFill;
import org.apache.iotdb.db.qp.strategy.SqlBaseParser.*;
//import org.apache.iotdb.db.query.fill.IFill;
//import org.apache.iotdb.db.query.fill.LinearFill;
//import org.apache.iotdb.db.query.fill.PreviousFill;
import org.apache.iotdb.db.trigger.definition.HookID;
import org.apache.iotdb.tsfile.common.conf.TSFileDescriptor;
import org.apache.iotdb.tsfile.common.constant.TsFileConstant;
import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.read.common.Path;
import org.apache.iotdb.tsfile.read.filter.operator.In;
import org.apache.iotdb.tsfile.utils.StringContainer;

/**
 * This class is a listener and you can get an operator which is a logical plan.
 */
public class LogicalGenerator extends SqlBaseBaseListener {

  private RootOperator initializedOperator = null;
  private ZoneId zoneId;
  private int operatorType;
  private CreateTimeSeriesOperator createTimeSeriesOperator;
  private AlterTimeSeriesOperator alterTimeSeriesOperator;
  private InsertOperator insertOp;
  private SelectOperator selectOp;
  private UpdateOperator updateOp;
  private QueryOperator queryOp;
  private DeleteDataOperator deleteDataOp;

  LogicalGenerator(ZoneId zoneId) {
    this.zoneId = zoneId;
  }

  RootOperator getLogicalPlan() {
    return initializedOperator;
  }

  @Override
  public void enterCountTimeseries(CountTimeseriesContext ctx) {
    super.enterCountTimeseries(ctx);
    if (ctx.INT() != null) {
      initializedOperator = new CountOperator(SQLConstant.TOK_COUNT_NODE_TIMESERIES,
          parsePrefixPath(ctx.prefixPath()), Integer.parseInt(ctx.INT().getText()));
    } else {
      if(ctx.prefixPath() != null) {
        initializedOperator = new CountOperator(SQLConstant.TOK_COUNT_TIMESERIES,
            parsePrefixPath(ctx.prefixPath()));
      } else {
        initializedOperator = new CountOperator(SQLConstant.TOK_COUNT_TIMESERIES,
            new Path(SQLConstant.ROOT));
      }
    }
  }

  @Override
  public void enterFlush(FlushContext ctx) {
    super.enterFlush(ctx);
    FlushOperator flushOperator = new FlushOperator(SQLConstant.TOK_FLUSH);
    if(ctx.booleanClause() != null) {
        flushOperator.setSeq(Boolean.parseBoolean(ctx.booleanClause().getText()));
    }
    if(ctx.prefixPath(0) != null) {
      List<Path> storageGroups = new ArrayList<>();
      for(PrefixPathContext prefixPathContext : ctx.prefixPath()) {
        storageGroups.add(parsePrefixPath(prefixPathContext));
      }
      flushOperator.setStorageGroupList(storageGroups);
    }

    initializedOperator = flushOperator;
  }

  @Override
  public void enterMerge(MergeContext ctx) {
    super.enterMerge(ctx);
    initializedOperator = new MergeOperator(SQLConstant.TOK_MERGE);
  }

  @Override
  public void enterFullMerge(FullMergeContext ctx) {
    super.enterFullMerge(ctx);
    initializedOperator = new MergeOperator(SQLConstant.TOK_FULL_MERGE);
  }

  @Override
  public void enterClearcache(SqlBaseParser.ClearcacheContext ctx) {
    super.enterClearcache(ctx);
    initializedOperator = new ClearCacheOperator(SQLConstant.TOK_CLEAR_CACHE);
  }

  @Override
  public void enterCountNodes(CountNodesContext ctx) {
    super.enterCountNodes(ctx);
    initializedOperator = new CountOperator(SQLConstant.TOK_COUNT_NODES,
        parsePrefixPath(ctx.prefixPath()), Integer.parseInt(ctx.INT().getText()));
  }

  @Override
  public void enterShowDevices(ShowDevicesContext ctx) {
    super.enterShowDevices(ctx);
    if (ctx.prefixPath() != null) {
      initializedOperator = new ShowDevicesOperator(SQLConstant.TOK_DEVICES,
          parsePrefixPath(ctx.prefixPath()));
    } else {
      initializedOperator = new ShowDevicesOperator(SQLConstant.TOK_DEVICES,
          new Path(SQLConstant.ROOT));
    }
  }

  @Override
  public void enterShowChildPaths(ShowChildPathsContext ctx) {
    super.enterShowChildPaths(ctx);
    if (ctx.prefixPath() != null) {
      initializedOperator = new ShowChildPathsOperator(SQLConstant.TOK_CHILD_PATHS,
          parsePrefixPath(ctx.prefixPath()));
    } else {
      initializedOperator = new ShowChildPathsOperator(SQLConstant.TOK_CHILD_PATHS,
          new Path(SQLConstant.ROOT));
    }
  }

  @Override
  public void enterShowStorageGroup(ShowStorageGroupContext ctx) {
    super.enterShowStorageGroup(ctx);
    initializedOperator = new ShowOperator(SQLConstant.TOK_STORAGE_GROUP);
  }

  @Override
  public void enterLoadFiles(LoadFilesContext ctx) {
    super.enterLoadFiles(ctx);
    if(ctx.autoCreateSchema() != null) {
      if(ctx.autoCreateSchema().INT() != null) {
        initializedOperator = new LoadFilesOperator(new File(removeStringQuote(ctx.STRING_LITERAL().getText())),
            Boolean.parseBoolean(ctx.autoCreateSchema().booleanClause().getText()),
            Integer.parseInt(ctx.autoCreateSchema().INT().getText())
        );
      } else {
        initializedOperator = new LoadFilesOperator(new File(removeStringQuote(ctx.STRING_LITERAL().getText())),
            Boolean.parseBoolean(ctx.autoCreateSchema().booleanClause().getText()),
            IoTDBDescriptor.getInstance().getConfig().getDefaultStorageGroupLevel()
        );
      }
    } else {
      initializedOperator = new LoadFilesOperator(new File(removeStringQuote(ctx.STRING_LITERAL().getText())),
          true,
          IoTDBDescriptor.getInstance().getConfig().getDefaultStorageGroupLevel()
      );
    }
  }

  @Override
  public void enterMoveFile(MoveFileContext ctx) {
    super.enterMoveFile(ctx);
    initializedOperator = new MoveFileOperator(new File(removeStringQuote(ctx.STRING_LITERAL(0).getText())),
        new File(removeStringQuote(ctx.STRING_LITERAL(1).getText())));
  }

  @Override
  public void enterRemoveFile(RemoveFileContext ctx) {
    super.enterRemoveFile(ctx);
    initializedOperator = new RemoveFileOperator(new File(removeStringQuote(ctx.STRING_LITERAL().getText())));
  }

  @Override
  public void enterLoadConfigurationStatement(LoadConfigurationStatementContext ctx) {
    super.enterLoadConfigurationStatement(ctx);
    initializedOperator = new LoadConfigurationOperator();
  }

  @Override
  public void enterShowVersion(ShowVersionContext ctx) {
    super.enterShowVersion(ctx);
    initializedOperator = new ShowOperator(SQLConstant.TOK_VERSION);
  }

  @Override
  public void enterShowDynamicParameter(SqlBaseParser.ShowDynamicParameterContext ctx) {
    super.enterShowDynamicParameter(ctx);
    initializedOperator = new ShowOperator(SQLConstant.TOK_DYNAMIC_PARAMETER);
  }

  @Override
  public void enterShowFlushTaskInfo(SqlBaseParser.ShowFlushTaskInfoContext ctx) {
    super.enterShowFlushTaskInfo(ctx);
    initializedOperator = new ShowOperator(SQLConstant.TOK_FLUSH_TASK_INFO);
  }

  @Override
  public void enterShowTimeseries(ShowTimeseriesContext ctx) {
    super.enterShowTimeseries(ctx);
    if (ctx.prefixPath() != null) {
      initializedOperator = new ShowTimeSeriesOperator(SQLConstant.TOK_TIMESERIES,
          parsePrefixPath(ctx.prefixPath()));
    } else {
      initializedOperator = new ShowTimeSeriesOperator(SQLConstant.TOK_TIMESERIES,
          new Path("root"));
    }
  }

  @Override
  public void enterCreateTimeseries(CreateTimeseriesContext ctx) {
    super.enterCreateTimeseries(ctx);
    createTimeSeriesOperator = new CreateTimeSeriesOperator(SQLConstant.TOK_METADATA_CREATE);
    operatorType = SQLConstant.TOK_METADATA_CREATE;
    createTimeSeriesOperator.setPath(parseFullPath(ctx.fullPath()));
  }

  @Override
  public void enterAlterTimeseries(SqlBaseParser.AlterTimeseriesContext ctx) {
    super.enterAlterTimeseries(ctx);
    alterTimeSeriesOperator = new AlterTimeSeriesOperator(SQLConstant.TOK_METADATA_ALTER);
    operatorType = SQLConstant.TOK_METADATA_ALTER;
    alterTimeSeriesOperator.setPath(parseFullPath(ctx.fullPath()));
  }

  @Override
  public void enterAlterClause(SqlBaseParser.AlterClauseContext ctx) {
    super.enterAlterClause(ctx);
    Map<String, String> alterMap = new HashMap<>();
    // rename
    if (ctx.RENAME() != null) {
      alterTimeSeriesOperator.setAlterType(AlterType.RENAME);
      alterMap.put(ctx.beforeName.getText(), ctx.currentName.getText());
    } else if (ctx.SET() != null) {
      // set
      alterTimeSeriesOperator.setAlterType(AlterType.SET);
      setMap(ctx, alterMap);
    } else if (ctx.DROP() != null) {
      // drop
      alterTimeSeriesOperator.setAlterType(AlterType.DROP);
      for (TerminalNode dropId : ctx.ID()) {
        alterMap.put(dropId.getText(), null);
      }
    } else if (ctx.TAGS() != null) {
      // add tag
      alterTimeSeriesOperator.setAlterType(AlterType.ADD_TAGS);
      setMap(ctx, alterMap);
    } else if (ctx.ATTRIBUTES() != null){
      // add attribute
      alterTimeSeriesOperator.setAlterType(AlterType.ADD_ATTRIBUTES);
      setMap(ctx, alterMap);
    } else {
      // upsert
      alterTimeSeriesOperator.setAlterType(AlterType.UPSERT);
    }
    alterTimeSeriesOperator.setAlterMap(alterMap);
    initializedOperator = alterTimeSeriesOperator;
  }

  private void setMap(SqlBaseParser.AlterClauseContext ctx, Map<String, String> alterMap) {
    List<PropertyContext> tagsList = ctx.property();
    if (ctx.property(0) != null) {
      for (PropertyContext property : tagsList) {
        String value;
        if(property.propertyValue().STRING_LITERAL() != null) {
          value = removeStringQuote(property.propertyValue().getText());
        } else {
          value = property.propertyValue().getText();
        }
        alterMap.put(property.ID().getText(), value);
      }
    }
  }

  @Override
  public void enterAlias(AliasContext ctx) {
    super.enterAlias(ctx);
    createTimeSeriesOperator.setAlias(ctx.ID().getText());
  }

  @Override
  public void enterCreateUser(CreateUserContext ctx) {
    super.enterCreateUser(ctx);
    AuthorOperator authorOperator = new AuthorOperator(SQLConstant.TOK_AUTHOR_CREATE,
        AuthorOperator.AuthorType.CREATE_USER);
    authorOperator.setUserName(ctx.ID().getText());
    authorOperator.setPassWord(removeStringQuote(ctx.password.getText()));
    initializedOperator = authorOperator;
    operatorType = SQLConstant.TOK_AUTHOR_CREATE;
  }

  @Override
  public void enterCreateRole(CreateRoleContext ctx) {
    super.enterCreateRole(ctx);
    AuthorOperator authorOperator = new AuthorOperator(SQLConstant.TOK_AUTHOR_CREATE,
        AuthorOperator.AuthorType.CREATE_ROLE);
    authorOperator.setRoleName(ctx.ID().getText());
    initializedOperator = authorOperator;
    operatorType = SQLConstant.TOK_AUTHOR_CREATE;
  }

  @Override
  public void enterAlterUser(AlterUserContext ctx) {
    super.enterAlterUser(ctx);
    AuthorOperator authorOperator = new AuthorOperator(SQLConstant.TOK_AUTHOR_UPDATE_USER,
        AuthorOperator.AuthorType.UPDATE_USER);
    if (ctx.ID() != null) {
      authorOperator.setUserName(ctx.ID().getText());
    } else {
      authorOperator.setUserName(ctx.ROOT().getText());
    }
    authorOperator.setNewPassword(removeStringQuote(ctx.password.getText()));
    initializedOperator = authorOperator;
    operatorType = SQLConstant.TOK_AUTHOR_UPDATE_USER;
  }

  @Override
  public void enterDropUser(DropUserContext ctx) {
    super.enterDropUser(ctx);
    AuthorOperator authorOperator = new AuthorOperator(SQLConstant.TOK_AUTHOR_DROP,
        AuthorOperator.AuthorType.DROP_USER);
    authorOperator.setUserName(ctx.ID().getText());
    initializedOperator = authorOperator;
    operatorType = SQLConstant.TOK_AUTHOR_DROP;
  }

  @Override
  public void enterDropRole(DropRoleContext ctx) {
    super.enterDropRole(ctx);
    AuthorOperator authorOperator = new AuthorOperator(SQLConstant.TOK_AUTHOR_DROP,
        AuthorOperator.AuthorType.DROP_ROLE);
    authorOperator.setRoleName(ctx.ID().getText());
    initializedOperator = authorOperator;
    operatorType = SQLConstant.TOK_AUTHOR_DROP;
  }

  @Override
  public void enterGrantUser(GrantUserContext ctx) {
    super.enterGrantUser(ctx);
    AuthorOperator authorOperator = new AuthorOperator(SQLConstant.TOK_AUTHOR_GRANT,
        AuthorOperator.AuthorType.GRANT_USER);
    authorOperator.setUserName(ctx.ID().getText());
    authorOperator.setPrivilegeList(parsePrivilege(ctx.privileges()));
    authorOperator.setNodeNameList(parsePrefixPath(ctx.prefixPath()));
    initializedOperator = authorOperator;
    operatorType = SQLConstant.TOK_AUTHOR_GRANT;
  }

  @Override
  public void enterGrantRole(GrantRoleContext ctx) {
    super.enterGrantRole(ctx);
    AuthorOperator authorOperator = new AuthorOperator(SQLConstant.TOK_AUTHOR_GRANT,
        AuthorType.GRANT_ROLE);
    authorOperator.setRoleName(ctx.ID().getText());
    authorOperator.setPrivilegeList(parsePrivilege(ctx.privileges()));
    authorOperator.setNodeNameList(parsePrefixPath(ctx.prefixPath()));
    initializedOperator = authorOperator;
    operatorType = SQLConstant.TOK_AUTHOR_GRANT;
  }

  @Override
  public void enterRevokeUser(RevokeUserContext ctx) {
    super.enterRevokeUser(ctx);
    AuthorOperator authorOperator = new AuthorOperator(SQLConstant.TOK_AUTHOR_GRANT,
        AuthorType.REVOKE_USER);
    authorOperator.setUserName(ctx.ID().getText());
    authorOperator.setPrivilegeList(parsePrivilege(ctx.privileges()));
    authorOperator.setNodeNameList(parsePrefixPath(ctx.prefixPath()));
    initializedOperator = authorOperator;
    operatorType = SQLConstant.TOK_AUTHOR_GRANT;
  }

  @Override
  public void enterRevokeRole(RevokeRoleContext ctx) {
    super.enterRevokeRole(ctx);
    AuthorOperator authorOperator = new AuthorOperator(SQLConstant.TOK_AUTHOR_GRANT,
        AuthorType.REVOKE_ROLE);
    authorOperator.setRoleName(ctx.ID().getText());
    authorOperator.setPrivilegeList(parsePrivilege(ctx.privileges()));
    authorOperator.setNodeNameList(parsePrefixPath(ctx.prefixPath()));
    initializedOperator = authorOperator;
    operatorType = SQLConstant.TOK_AUTHOR_GRANT;
  }

  @Override
  public void enterGrantRoleToUser(GrantRoleToUserContext ctx) {
    super.enterGrantRoleToUser(ctx);
    AuthorOperator authorOperator = new AuthorOperator(SQLConstant.TOK_AUTHOR_GRANT,
        AuthorOperator.AuthorType.GRANT_ROLE_TO_USER);
    authorOperator.setRoleName(ctx.roleName.getText());
    authorOperator.setUserName(ctx.userName.getText());
    initializedOperator = authorOperator;
    operatorType = SQLConstant.TOK_AUTHOR_GRANT;
  }

  @Override
  public void enterRevokeRoleFromUser(RevokeRoleFromUserContext ctx) {
    super.enterRevokeRoleFromUser(ctx);
    AuthorOperator authorOperator = new AuthorOperator(SQLConstant.TOK_AUTHOR_GRANT,
        AuthorType.REVOKE_ROLE_FROM_USER);
    authorOperator.setRoleName(ctx.roleName.getText());
    authorOperator.setUserName(ctx.userName.getText());
    initializedOperator = authorOperator;
    operatorType = SQLConstant.TOK_AUTHOR_GRANT;
  }

  @Override
  public void enterLoadStatement(LoadStatementContext ctx) {
    super.enterLoadStatement(ctx);
    if (ctx.prefixPath().nodeName().size() < 3) {
      throw new SQLParserException("data load command: child count < 3\n");
    }

    String csvPath = ctx.STRING_LITERAL().getText();
    StringContainer sc = new StringContainer(TsFileConstant.PATH_SEPARATOR);
    List<NodeNameContext> nodeNames = ctx.prefixPath().nodeName();
    sc.addTail(ctx.prefixPath().ROOT().getText());
    for (NodeNameContext nodeName : nodeNames) {
      sc.addTail(nodeName.getText());
    }
    initializedOperator = new LoadDataOperator(SQLConstant.TOK_DATALOAD,
        removeStringQuote(csvPath),
        sc.toString());
    operatorType = SQLConstant.TOK_DATALOAD;
  }

  @Override
  public void enterGrantWatermarkEmbedding(GrantWatermarkEmbeddingContext ctx) {
    super.enterGrantWatermarkEmbedding(ctx);
    List<RootOrIdContext> rootOrIdList = ctx.rootOrId();
    List<String> users = new ArrayList<>();
    for (RootOrIdContext rootOrId : rootOrIdList) {
      users.add(rootOrId.getText());
    }
    initializedOperator = new DataAuthOperator(SQLConstant.TOK_GRANT_WATERMARK_EMBEDDING, users);
  }

  @Override
  public void enterRevokeWatermarkEmbedding(RevokeWatermarkEmbeddingContext ctx) {
    super.enterRevokeWatermarkEmbedding(ctx);
    List<RootOrIdContext> rootOrIdList = ctx.rootOrId();
    List<String> users = new ArrayList<>();
    for (RootOrIdContext rootOrId : rootOrIdList) {
      users.add(rootOrId.getText());
    }
    initializedOperator = new DataAuthOperator(SQLConstant.TOK_REVOKE_WATERMARK_EMBEDDING, users);
    operatorType = SQLConstant.TOK_REVOKE_WATERMARK_EMBEDDING;
  }

  @Override
  public void enterListUser(ListUserContext ctx) {
    super.enterListUser(ctx);
    initializedOperator = new AuthorOperator(SQLConstant.TOK_LIST,
        AuthorOperator.AuthorType.LIST_USER);
    operatorType = SQLConstant.TOK_LIST;
  }

  @Override
  public void enterListRole(ListRoleContext ctx) {
    super.enterListRole(ctx);
    initializedOperator = new AuthorOperator(SQLConstant.TOK_LIST,
        AuthorOperator.AuthorType.LIST_ROLE);
    operatorType = SQLConstant.TOK_LIST;
  }

  @Override
  public void enterListPrivilegesUser(ListPrivilegesUserContext ctx) {
    super.enterListPrivilegesUser(ctx);
    AuthorOperator operator = new AuthorOperator(SQLConstant.TOK_LIST,
        AuthorOperator.AuthorType.LIST_USER_PRIVILEGE);
    operator.setUserName(ctx.ID().getText());
    operator.setNodeNameList(parsePrefixPath(ctx.prefixPath()));
    initializedOperator = operator;
    operatorType = SQLConstant.TOK_LIST;
  }

  @Override
  public void enterListPrivilegesRole(ListPrivilegesRoleContext ctx) {
    super.enterListPrivilegesRole(ctx);
    AuthorOperator operator = new AuthorOperator(SQLConstant.TOK_LIST,
        AuthorOperator.AuthorType.LIST_ROLE_PRIVILEGE);
    operator.setRoleName((ctx.ID().getText()));
    operator.setNodeNameList(parsePrefixPath(ctx.prefixPath()));
    initializedOperator = operator;
    operatorType = SQLConstant.TOK_LIST;
  }

  @Override
  public void enterListUserPrivileges(ListUserPrivilegesContext ctx) {
    super.enterListUserPrivileges(ctx);
    AuthorOperator operator = new AuthorOperator(SQLConstant.TOK_LIST,
        AuthorOperator.AuthorType.LIST_USER_PRIVILEGE);
    operator.setUserName(ctx.ID().getText());
    initializedOperator = operator;
    operatorType = SQLConstant.TOK_LIST;
  }

  @Override
  public void enterListRolePrivileges(ListRolePrivilegesContext ctx) {
    super.enterListRolePrivileges(ctx);
    AuthorOperator operator = new AuthorOperator(SQLConstant.TOK_LIST,
        AuthorOperator.AuthorType.LIST_ROLE_PRIVILEGE);
    operator.setRoleName(ctx.ID().getText());
    initializedOperator = operator;
    operatorType = SQLConstant.TOK_LIST;
  }

  @Override
  public void enterListAllRoleOfUser(ListAllRoleOfUserContext ctx) {
    super.enterListAllRoleOfUser(ctx);
    AuthorOperator operator = new AuthorOperator(SQLConstant.TOK_LIST,
        AuthorOperator.AuthorType.LIST_USER_ROLES);
    initializedOperator = operator;
    operator.setUserName(ctx.ID().getText());
    operatorType = SQLConstant.TOK_LIST;
  }

  @Override
  public void enterListAllUserOfRole(ListAllUserOfRoleContext ctx) {
    super.enterListAllUserOfRole(ctx);
    AuthorOperator operator = new AuthorOperator(SQLConstant.TOK_LIST,
        AuthorOperator.AuthorType.LIST_ROLE_USERS);
    initializedOperator = operator;
    operator.setRoleName((ctx.ID().getText()));
    operatorType = SQLConstant.TOK_LIST;
  }

  @Override
  public void enterSetTTLStatement(SetTTLStatementContext ctx) {
    super.enterSetTTLStatement(ctx);
    SetTTLOperator operator = new SetTTLOperator(SQLConstant.TOK_SET);
    operator.setStorageGroup(parsePrefixPath(ctx.prefixPath()).getFullPath());
    operator.setDataTTL(Long.parseLong(ctx.INT().getText()));
    initializedOperator = operator;
    operatorType = SQLConstant.TOK_SET;
  }

  @Override
  public void enterUnsetTTLStatement(UnsetTTLStatementContext ctx) {
    super.enterUnsetTTLStatement(ctx);
    SetTTLOperator operator = new SetTTLOperator(SQLConstant.TOK_UNSET);
    operator.setStorageGroup(parsePrefixPath(ctx.prefixPath()).getFullPath());
    initializedOperator = operator;
    operatorType = SQLConstant.TOK_UNSET;
  }

  @Override
  public void enterShowTTLStatement(ShowTTLStatementContext ctx) {
    super.enterShowTTLStatement(ctx);
    List<String> storageGroups = new ArrayList<>();
    List<PrefixPathContext> prefixPathList = ctx.prefixPath();
    for (PrefixPathContext prefixPath : prefixPathList) {
      storageGroups.add(parsePrefixPath(prefixPath).getFullPath());
    }
    initializedOperator = new ShowTTLOperator(storageGroups);
  }

  @Override
  public void enterShowAllTTLStatement(ShowAllTTLStatementContext ctx) {
    super.enterShowAllTTLStatement(ctx);
    List<String> storageGroups = new ArrayList<>();
    initializedOperator = new ShowTTLOperator(storageGroups);
  }

  private String[] parsePrivilege(PrivilegesContext ctx) {
    List<TerminalNode> privilegeList = ctx.STRING_LITERAL();
    List<String> privileges = new ArrayList<>();
    for (TerminalNode privilege : privilegeList) {
      privileges.add(removeStringQuote(privilege.getText()));
    }
    return privileges.toArray(new String[0]);
  }

  private String removeStringQuote(String src) {
    if (src.charAt(0) == '\'' && src.charAt(src.length() - 1) == '\'') {
      return src.substring(1, src.length() - 1);
    } else if (src.charAt(0) == '\"' && src.charAt(src.length() - 1) == '\"') {
      return src.substring(1, src.length() - 1);
    } else {
      throw new SQLParserException("error format for string with quote:" + src);
    }
  }

  @Override
  public void enterDeleteTimeseries(DeleteTimeseriesContext ctx) {
    super.enterDeleteTimeseries(ctx);
    List<Path> deletePaths = new ArrayList<>();
    List<PrefixPathContext> prefixPaths = ctx.prefixPath();
    for (PrefixPathContext prefixPath : prefixPaths) {
      deletePaths.add(parsePrefixPath(prefixPath));
    }
    DeleteTimeSeriesOperator deleteTimeSeriesOperator = new DeleteTimeSeriesOperator(
        SQLConstant.TOK_METADATA_DELETE);
    deleteTimeSeriesOperator.setDeletePathList(deletePaths);
    initializedOperator = deleteTimeSeriesOperator;
    operatorType = SQLConstant.TOK_METADATA_DELETE;
  }

  @Override
  public void enterSetStorageGroup(SetStorageGroupContext ctx) {
    super.enterSetStorageGroup(ctx);
    SetStorageGroupOperator setStorageGroupOperator = new SetStorageGroupOperator(
        SQLConstant.TOK_METADATA_SET_FILE_LEVEL);
    Path path = parseFullPath(ctx.fullPath());
    setStorageGroupOperator.setPath(path);
    initializedOperator = setStorageGroupOperator;
    operatorType = SQLConstant.TOK_METADATA_SET_FILE_LEVEL;
  }

  @Override
  public void enterDeleteStorageGroup(DeleteStorageGroupContext ctx) {
    super.enterDeleteStorageGroup(ctx);
    List<Path> deletePaths = new ArrayList<>();
    List<FullPathContext> fullPaths = ctx.fullPath();
    for (FullPathContext fullPath : fullPaths) {
      deletePaths.add(parseFullPath(fullPath));
    }
    DeleteStorageGroupOperator deleteStorageGroupOperator = new DeleteStorageGroupOperator(
        SQLConstant.TOK_METADATA_DELETE_FILE_LEVEL);
    deleteStorageGroupOperator.setDeletePathList(deletePaths);
    initializedOperator = deleteStorageGroupOperator;
    operatorType = SQLConstant.TOK_METADATA_DELETE_FILE_LEVEL;
  }

  @Override
  public void enterDeleteStatement(DeleteStatementContext ctx) {
    super.enterDeleteStatement(ctx);
    operatorType = SQLConstant.TOK_DELETE;
    deleteDataOp = new DeleteDataOperator(SQLConstant.TOK_DELETE);
    selectOp = new SelectOperator(SQLConstant.TOK_SELECT);
    List<PrefixPathContext> prefixPaths = ctx.prefixPath();
    for (PrefixPathContext prefixPath : prefixPaths) {
      Path path = parsePrefixPath(prefixPath);
      selectOp.addSelectPath(path);
    }
    deleteDataOp.setSelectOperator(selectOp);
    initializedOperator = deleteDataOp;
  }

  @Override
  public void enterDisableAlign(SqlBaseParser.DisableAlignContext ctx) {
    super.enterDisableAlign(ctx);
    queryOp.setAlignByTime(false);
  }

  @Override
  public void enterGroupByFillClause(SqlBaseParser.GroupByFillClauseContext ctx) {
    super.enterGroupByFillClause(ctx);
    queryOp.setGroupBy(true);
    queryOp.setFill(true);
    queryOp.setLeftCRightO(ctx.timeInterval().LS_BRACKET() != null);


    // parse timeUnit
    queryOp.setUnit(parseDuration(ctx.DURATION().getText()));
    queryOp.setSlidingStep(queryOp.getUnit());

    parseTimeInterval(ctx.timeInterval());

    List<TypeClauseContext> list = ctx.typeClause();
    Map<TSDataType, IFill> fillTypes = new EnumMap<>(TSDataType.class);
    for (TypeClauseContext typeClause : list) {
      // group by fill doesn't support linear fill
      if (typeClause.linearClause() != null) {
        throw new SQLParserException("group by fill doesn't support linear fill");
      }
      // all type use the same fill way
      if (SQLConstant.ALL.equalsIgnoreCase(typeClause.dataType().getText())) {
        IFill fill;
        if (typeClause.previousUntilLastClause() != null) {
          long preRange;
          if (typeClause.previousUntilLastClause().DURATION() != null) {
            preRange = parseDuration(typeClause.previousUntilLastClause().DURATION().getText());
          } else {
            preRange = IoTDBDescriptor.getInstance().getConfig().getDefaultFillInterval();
          }
          fill = new PreviousFill(preRange, true);
        } else {
          long preRange;
          if (typeClause.previousClause().DURATION() != null) {
            preRange = parseDuration(typeClause.previousClause().DURATION().getText());
          } else {
            preRange = IoTDBDescriptor.getInstance().getConfig().getDefaultFillInterval();
          }
          fill = new PreviousFill(preRange);
        }
        for (TSDataType tsDataType : TSDataType.values()) {
          fillTypes.put(tsDataType, fill.copy());
        }
        break;
      } else {
        parseTypeClause(typeClause, fillTypes);
      }
    }
    queryOp.setFill(true);
    queryOp.setFillTypes(fillTypes);
  }

  private void parseTimeInterval(TimeIntervalContext timeInterval) {
    long startTime;
    long endTime;
    if (timeInterval.timeValue(0).INT() != null) {
      startTime = Long.parseLong(timeInterval.timeValue(0).INT().getText());
    } else if (timeInterval.timeValue(0).dateExpression() != null) {
      startTime = parseDateExpression(timeInterval.timeValue(0).dateExpression());
    } else {
      startTime = parseTimeFormat(timeInterval.timeValue(0).dateFormat().getText());
    }
    if (timeInterval.timeValue(1).INT() != null) {
      endTime = Long.parseLong(timeInterval.timeValue(1).INT().getText());
    } else if (timeInterval.timeValue(1).dateExpression() != null) {
      endTime = parseDateExpression(timeInterval.timeValue(1).dateExpression());
    } else {
      endTime = parseTimeFormat(timeInterval.timeValue(1).dateFormat().getText());
    }

    queryOp.setStartTime(startTime);
    queryOp.setEndTime(endTime);
  }

  @Override
  public void enterGroupByClause(GroupByClauseContext ctx) {
    super.enterGroupByClause(ctx);
    queryOp.setGroupBy(true);
    queryOp.setLeftCRightO(ctx.timeInterval().LS_BRACKET() != null);
    // parse timeUnit
    queryOp.setUnit(parseDuration(ctx.DURATION(0).getText()));
    queryOp.setSlidingStep(queryOp.getUnit());
    // parse sliding step
    if (ctx.DURATION().size() == 2) {
      queryOp.setSlidingStep(parseDuration(ctx.DURATION(1).getText()));
      if (queryOp.getSlidingStep() < queryOp.getUnit()) {
        throw new SQLParserException(
            "The third parameter sliding step shouldn't be smaller than the second parameter time interval.");
      }
    }

    parseTimeInterval(ctx.timeInterval());
  }

  @Override
  public void enterFillClause(FillClauseContext ctx) {
    super.enterFillClause(ctx);
    FilterOperator filterOperator = queryOp.getFilterOperator();
    if (!filterOperator.isLeaf() || filterOperator.getTokenIntType() != SQLConstant.EQUAL) {
      throw new SQLParserException("Only \"=\" can be used in fill function");
    }
    List<TypeClauseContext> list = ctx.typeClause();
    Map<TSDataType, IFill> fillTypes = new EnumMap<>(TSDataType.class);
    for (TypeClauseContext typeClause : list) {
      parseTypeClause(typeClause, fillTypes);
    }
    queryOp.setFill(true);
    queryOp.setFillTypes(fillTypes);
  }

  private void parseTypeClause(TypeClauseContext ctx, Map<TSDataType, IFill> fillTypes) {
    TSDataType dataType = parseType(ctx.dataType().getText());
    if (ctx.linearClause() != null && dataType == TSDataType.TEXT) {
      throw new SQLParserException(String.format("type %s cannot use %s fill function"
          , dataType, ctx.linearClause().LINEAR().getText()));
    }

    int defaultFillInterval = IoTDBDescriptor.getInstance().getConfig().getDefaultFillInterval();

    if (ctx.linearClause() != null) {  // linear
      if (ctx.linearClause().DURATION(0) != null) {
        long beforeRange = parseDuration(ctx.linearClause().DURATION(0).getText());
        long afterRange = parseDuration(ctx.linearClause().DURATION(1).getText());
        fillTypes.put(dataType, new LinearFill(beforeRange, afterRange));
      } else {
        fillTypes.put(dataType, new LinearFill(defaultFillInterval, defaultFillInterval));
      }
    } else if (ctx.previousClause() != null) { // previous
      if (ctx.previousClause().DURATION() != null) {
        long preRange = parseDuration(ctx.previousClause().DURATION().getText());
        fillTypes.put(dataType, new PreviousFill(preRange));
      } else {
        fillTypes.put(dataType, new PreviousFill(defaultFillInterval));
      }
    } else { // previous until last
      if (ctx.previousUntilLastClause().DURATION() != null) {
        long preRange = parseDuration(ctx.previousUntilLastClause().DURATION().getText());
        fillTypes.put(dataType, new PreviousFill(preRange, true));
      } else {
        fillTypes.put(dataType, new PreviousFill(defaultFillInterval, true));
      }
    }
  }

  @Override
  public void enterAlignByDeviceClause(AlignByDeviceClauseContext ctx) {
    super.enterAlignByDeviceClause(ctx);
    queryOp.setAlignByDevice(true);
  }

  /**
   * parse datatype node.
   */
  private TSDataType parseType(String datatype) {
    String type = datatype.toLowerCase();
    switch (type) {
      case "int32":
        return TSDataType.INT32;
      case "int64":
        return TSDataType.INT64;
      case "float":
        return TSDataType.FLOAT;
      case "double":
        return TSDataType.DOUBLE;
      case "boolean":
        return TSDataType.BOOLEAN;
      case "text":
        return TSDataType.TEXT;
      default:
        throw new SQLParserException("not a valid fill type : " + type);
    }
  }

  @Override
  public void enterLimitClause(LimitClauseContext ctx) {
    super.enterLimitClause(ctx);
    int limit;
    try {
      limit = Integer.parseInt(ctx.INT().getText());
    } catch (NumberFormatException e) {
      throw new SQLParserException("Out of range. LIMIT <N>: N should be Int32.");
    }
    if (limit <= 0) {
      throw new SQLParserException("LIMIT <N>: N should be greater than 0.");
    }
    if (initializedOperator instanceof ShowTimeSeriesOperator) {
      ((ShowTimeSeriesOperator) initializedOperator).setLimit(limit);
    } else {
      queryOp.setRowLimit(limit);
    }
  }

  @Override
  public void enterOffsetClause(OffsetClauseContext ctx) {
    super.enterOffsetClause(ctx);
    int offset;
    try {
      offset = Integer.parseInt(ctx.INT().getText());
    } catch (NumberFormatException e) {
      throw new SQLParserException(
          "Out of range. OFFSET <OFFSETValue>: OFFSETValue should be Int32.");
    }
    if (offset < 0) {
      throw new SQLParserException("OFFSET <OFFSETValue>: OFFSETValue should >= 0.");
    }
    if (initializedOperator instanceof ShowTimeSeriesOperator) {
      ((ShowTimeSeriesOperator) initializedOperator).setOffset(offset);
    } else {
      queryOp.setRowOffset(offset);
    }
  }

  @Override
  public void enterSlimitClause(SlimitClauseContext ctx) {
    super.enterSlimitClause(ctx);
    int slimit;
    try {
      slimit = Integer.parseInt(ctx.INT().getText());
    } catch (NumberFormatException e) {
      throw new SQLParserException(
          "Out of range. SLIMIT <SN>: SN should be Int32.");
    }
    if (slimit <= 0) {
      throw new SQLParserException("SLIMIT <SN>: SN should be greater than 0.");
    }
    queryOp.setSeriesLimit(slimit);
  }

  @Override
  public void enterSoffsetClause(SoffsetClauseContext ctx) {
    super.enterSoffsetClause(ctx);
    int soffset;
    try {
      soffset = Integer.parseInt(ctx.INT().getText());
    } catch (NumberFormatException e) {
      throw new SQLParserException(
          "Out of range. SOFFSET <SOFFSETValue>: SOFFSETValue should be Int32.");
    }
    if (soffset < 0) {
      throw new SQLParserException(
          "SOFFSET <SOFFSETValue>: SOFFSETValue should >= 0.");
    }
    queryOp.setSeriesOffset(soffset);
  }

  @Override
  public void enterInsertColumnSpec(InsertColumnSpecContext ctx) {
    super.enterInsertColumnSpec(ctx);
    List<NodeNameWithoutStarContext> nodeNamesWithoutStar = ctx.nodeNameWithoutStar();
    List<String> measurementList = new ArrayList<>();
    for (NodeNameWithoutStarContext nodeNameWithoutStar : nodeNamesWithoutStar) {
      String measurement = nodeNameWithoutStar.getText();
      if (measurement.contains("\"") || measurement.contains("\'")) {
        measurement = measurement.substring(1, measurement.length() - 1);
      }
      measurementList.add(measurement);
    }
    insertOp.setMeasurementList(measurementList.toArray(new String[0]));
  }

  @Override
  public void enterInsertValuesSpec(InsertValuesSpecContext ctx) {
    super.enterInsertValuesSpec(ctx);
    long timestamp;
    if (ctx.dateFormat() != null) {
      timestamp = parseTimeFormat(ctx.dateFormat().getText());
    } else {
      timestamp = Long.parseLong(ctx.INT().getText());
    }
    insertOp.setTime(timestamp);
    List<String> valueList = new ArrayList<>();
    List<ConstantContext> values = ctx.constant();
    for (ConstantContext value : values) {
      valueList.add(value.getText());
    }
    insertOp.setValueList(valueList.toArray(new String[0]));
    initializedOperator = insertOp;
  }

  private Path parseFullPath(FullPathContext ctx) {
    List<NodeNameWithoutStarContext> nodeNamesWithoutStar = ctx.nodeNameWithoutStar();
    List<String> path = new ArrayList<>();
    if (ctx.ROOT() != null) {
      path.add(ctx.ROOT().getText());
    }
    for (NodeNameWithoutStarContext nodeNameWithoutStar : nodeNamesWithoutStar) {
      path.add(nodeNameWithoutStar.getText());
    }
    return new Path(
        new StringContainer(path.toArray(new String[0]), TsFileConstant.PATH_SEPARATOR));
  }

  @Override
  public void enterAttributeClauses(AttributeClausesContext ctx) {
    super.enterAttributeClauses(ctx);
    String dataType = ctx.dataType().getChild(0).getText().toUpperCase();
    String encoding = ctx.encoding().getChild(0).getText().toUpperCase();
    createTimeSeriesOperator.setDataType(TSDataType.valueOf(dataType));
    createTimeSeriesOperator.setEncoding(TSEncoding.valueOf(encoding));
    CompressionType compressor;
    List<PropertyContext> properties = ctx.property();
    Map<String, String> props = new HashMap<>(properties.size());
    if (ctx.propertyValue() != null) {
      compressor = CompressionType.valueOf(ctx.propertyValue().getText().toUpperCase());
    } else {
      compressor = TSFileDescriptor.getInstance().getConfig().getCompressor();
    }
    checkMetadataArgs(dataType, encoding, compressor.toString().toUpperCase());
    if (ctx.property(0) != null) {
      for (PropertyContext property : properties) {
        props.put(property.ID().getText().toLowerCase(),
                property.propertyValue().getText().toLowerCase());
      }
    }
    createTimeSeriesOperator.setCompressor(compressor);
    createTimeSeriesOperator.setProps(props);
    initializedOperator = createTimeSeriesOperator;
  }

  @Override
  public void enterAttributeClause(AttributeClauseContext ctx) {
    super.enterAttributeClause(ctx);
    Map<String, String> attributes = extractMap(ctx.property(), ctx.property(0));
    if (createTimeSeriesOperator != null) {
      createTimeSeriesOperator.setAttributes(attributes);
    } else if (alterTimeSeriesOperator != null) {
      alterTimeSeriesOperator.setAttributesMap(attributes);
    }
  }

  @Override
  public void enterTagClause(TagClauseContext ctx) {
    super.enterTagClause(ctx);
    Map<String, String> tags = extractMap(ctx.property(), ctx.property(0));
    if (createTimeSeriesOperator != null) {
      createTimeSeriesOperator.setTags(tags);
    } else if (alterTimeSeriesOperator != null) {
      alterTimeSeriesOperator.setTagsMap(tags);
    }
  }

  private Map<String, String> extractMap(List<PropertyContext> property2,
      PropertyContext property3) {
    String value;
    Map<String, String> tags = new HashMap<>(property2.size());
    if (property3 != null) {
      for (PropertyContext property : property2) {
        if (property.propertyValue().STRING_LITERAL() != null) {
          value = removeStringQuote(property.propertyValue().getText());
        } else {
          value = property.propertyValue().getText();
        }
        tags.put(property.ID().getText(), value);
      }
    }
    return tags;
  }

  @Override
  public void enterInsertStatement(InsertStatementContext ctx) {
    super.enterInsertStatement(ctx);
    insertOp = new InsertOperator(SQLConstant.TOK_INSERT);
    selectOp = new SelectOperator(SQLConstant.TOK_SELECT);
    operatorType = SQLConstant.TOK_INSERT;
    selectOp.addSelectPath(parseFullPath(ctx.fullPath()));
    insertOp.setSelectOperator(selectOp);
  }

  @Override
  public void enterUpdateStatement(UpdateStatementContext ctx) {
    super.enterUpdateStatement(ctx);
    updateOp = new UpdateOperator(SQLConstant.TOK_UPDATE);
    FromOperator fromOp = new FromOperator(SQLConstant.TOK_FROM);
    fromOp.addPrefixTablePath(parsePrefixPath(ctx.prefixPath()));
    selectOp = new SelectOperator(SQLConstant.TOK_SELECT);
    operatorType = SQLConstant.TOK_UPDATE;
    initializedOperator = updateOp;
  }

  @Override
  public void enterSelectStatement(SelectStatementContext ctx) {
    super.enterSelectStatement(ctx);
    operatorType = SQLConstant.TOK_QUERY;
    queryOp = new QueryOperator(SQLConstant.TOK_QUERY);
    initializedOperator = queryOp;
  }

  @Override
  public void enterSelectConstElement(SelectConstElementContext ctx) {
    super.enterSelectConstElement(ctx);
    operatorType = SQLConstant.TOK_QUERY;
    queryOp = new QueryOperator(SQLConstant.TOK_QUERY);
    initializedOperator = queryOp;
  }

  @Override
  public void enterFromClause(FromClauseContext ctx) {
    super.enterFromClause(ctx);
    FromOperator fromOp = new FromOperator(SQLConstant.TOK_FROM);
    List<PrefixPathContext> prefixFromPaths = ctx.prefixPath();
    for (PrefixPathContext prefixFromPath : prefixFromPaths) {
      Path path = parsePrefixPath(prefixFromPath);
      fromOp.addPrefixTablePath(path);
    }
    queryOp.setFromOperator(fromOp);
  }

  @Override
  public void enterFunctionElement(FunctionElementContext ctx) {
    super.enterFunctionElement(ctx);
    selectOp = new SelectOperator(SQLConstant.TOK_SELECT);
    List<FunctionCallContext> functionCallContextList = ctx.functionCall();
    for (FunctionCallContext functionCallContext : functionCallContextList) {
      Path path = parseSuffixPath(functionCallContext.suffixPath());
      selectOp.addClusterPath(path, functionCallContext.functionName().getText());
    }
    queryOp.setSelectOperator(selectOp);
  }

  @Override
  public void enterSelectElement(SelectElementContext ctx) {
    super.enterSelectElement(ctx);
    selectOp = new SelectOperator(SQLConstant.TOK_SELECT);
    List<SuffixPathContext> suffixPaths = ctx.suffixPath();
    for (SuffixPathContext suffixPath : suffixPaths) {
      Path path = parseSuffixPath(suffixPath);
      selectOp.addSelectPath(path);
    }
    queryOp.setSelectOperator(selectOp);
  }

  @Override
  public void enterLastElement(SqlBaseParser.LastElementContext ctx) {
    super.enterLastElement(ctx);
    selectOp = new SelectOperator(SQLConstant.TOK_SELECT);
    selectOp.setLastQuery();
    LastClauseContext lastClauseContext = ctx.lastClause();
    List<SuffixPathContext> suffixPaths = lastClauseContext.suffixPath();
    for (SuffixPathContext suffixPath : suffixPaths) {
      Path path = parseSuffixPath(suffixPath);
      selectOp.addSelectPath(path);
    }
    queryOp.setSelectOperator(selectOp);
  }

  @Override
  public void enterSetCol(SetColContext ctx) {
    super.enterSetCol(ctx);
    selectOp.addSelectPath(parseSuffixPath(ctx.suffixPath()));
    updateOp.setSelectOperator(selectOp);
    updateOp.setValue(ctx.constant().getText());
  }


  private Path parsePrefixPath(PrefixPathContext ctx) {
    List<NodeNameContext> nodeNames = ctx.nodeName();
    List<String> path = new ArrayList<>();
    path.add(ctx.ROOT().getText());
    for (NodeNameContext nodeName : nodeNames) {
      path.add(nodeName.getText());
    }
    return new Path(
        new StringContainer(path.toArray(new String[0]), TsFileConstant.PATH_SEPARATOR));
  }

  /**
   * parse duration to time value.
   *
   * @param durationStr represent duration string like: 12d8m9ns, 1y1mo, etc.
   * @return time in milliseconds, microseconds, or nanoseconds depending on the profile
   */
  private Long parseDuration(String durationStr) {
    String timestampPrecision = IoTDBDescriptor.getInstance().getConfig().getTimestampPrecision();

    long total = 0;
    long tmp = 0;
    for (int i = 0; i < durationStr.length(); i++) {
      char ch = durationStr.charAt(i);
      if (Character.isDigit(ch)) {
        tmp *= 10;
        tmp += (ch - '0');
      } else {
        String unit = durationStr.charAt(i) + "";
        // This is to identify units with two letters.
        if (i + 1 < durationStr.length() && !Character.isDigit(durationStr.charAt(i + 1))) {
          i++;
          unit += durationStr.charAt(i);
        }
        total += DatetimeUtils
            .convertDurationStrToLong(tmp, unit.toLowerCase(), timestampPrecision);
        tmp = 0;
      }
    }
    if (total <= 0) {
      throw new SQLParserException("Interval must more than 0.");
    }
    return total;
  }

  @Override
  public void enterWhereClause(WhereClauseContext ctx) {
    super.enterWhereClause(ctx);
    FilterOperator whereOp = new FilterOperator(SQLConstant.TOK_WHERE);
    whereOp.addChildOperator(parseOrExpression(ctx.orExpression()));
    switch (operatorType) {
      case SQLConstant.TOK_DELETE:
        deleteDataOp.setFilterOperator(whereOp.getChildren().get(0));
        long deleteTime = parseDeleteTimeFilter(deleteDataOp);
        deleteDataOp.setTime(deleteTime);
        break;
      case SQLConstant.TOK_QUERY:
        queryOp.setFilterOperator(whereOp.getChildren().get(0));
        break;
      case SQLConstant.TOK_UPDATE:
        updateOp.setFilterOperator(whereOp.getChildren().get(0));
        break;
      default:
        throw new SQLParserException("Where only support select, delete, update.");
    }
  }

  @Override
  public void enterShowWhereClause(ShowWhereClauseContext ctx) {
    super.enterShowWhereClause(ctx);

    ShowTimeSeriesOperator operator = (ShowTimeSeriesOperator) initializedOperator;
    PropertyValueContext propertyValueContext;
    if (ctx.containsExpression() != null) {
      operator.setContains(true);
      propertyValueContext = ctx.containsExpression().propertyValue();
      operator.setKey(ctx.containsExpression().ID().getText());
    } else {
      operator.setContains(false);
      propertyValueContext = ctx.property().propertyValue();
      operator.setKey(ctx.property().ID().getText());
    }
    String value;
    if(propertyValueContext.STRING_LITERAL() != null) {
      value = removeStringQuote(propertyValueContext.getText());
    } else {
      value = propertyValueContext.getText();
    }
    operator.setValue(value);
  }

  @Override
  public void enterCreateTrigger(CreateTriggerContext ctx) {
    initializedOperator = new CreateTriggerOperator(SQLConstant.TOK_TRIGGER_CREATE);
    operatorType = SQLConstant.TOK_TRIGGER_CREATE;
    CreateTriggerOperator operator = (CreateTriggerOperator) initializedOperator;
    operator.setId(ctx.triggerName.getText());
    operator.setPath(parseFullPath(ctx.fullPath()).getFullPath());
  }

  @Override
  public void enterTriggerEvent(TriggerEventContext ctx) {
    CreateTriggerOperator operator = (CreateTriggerOperator) initializedOperator;
    if (ctx.ALL() != null) {
      operator.enableHook(HookID.ON_ALL_EVENTS);
      return;
    }
    if (ctx.BEFORE() != null) {
      if (ctx.INSERT() != null) {
        if (ctx.BATCH() != null) {
          operator.enableHook(HookID.BEFORE_BATCH_INSERT);
        } else {
          operator.enableHook(HookID.BEFORE_INSERT);
        }
      } else if (ctx.DELETE() != null) {
        operator.enableHook(HookID.BEFORE_DELETE);
      } else if (ctx.UPDATE() != null) {
        throw new UnsupportedOperationException("update trigger is not supported.");
//        operator.enableHook(HookID.BEFORE_UPDATE);
      }
    } else if (ctx.AFTER() != null) {
      if (ctx.INSERT() != null) {
        if (ctx.BATCH() != null) {
          operator.enableHook(HookID.AFTER_BATCH_INSERT);
        } else {
          operator.enableHook(HookID.AFTER_INSERT);
        }
      } else if (ctx.DELETE() != null) {
        operator.enableHook(HookID.AFTER_DELETE);
      } else if (ctx.UPDATE() != null) {
        throw new UnsupportedOperationException("update trigger is not supported.");
//        operator.enableHook(HookID.AFTER_UPDATE);
      }
    }
  }

  @Override
  public void enterTriggerAttributeClause(TriggerAttributeClauseContext ctx) {
    CreateTriggerOperator operator = (CreateTriggerOperator) initializedOperator;
    String classNameWithQuotes = ctx.className.getText();
    operator.setClassName(classNameWithQuotes.substring(1, classNameWithQuotes.length() - 1));
  }

  @Override
  public void enterKeyValuePair(KeyValuePairContext ctx) {
    CreateTriggerOperator operator = (CreateTriggerOperator) initializedOperator;
    operator.addParameter(ctx.key.getText(), ctx.value.getText());
  }

  @Override
  public void enterDropTrigger(DropTriggerContext ctx) {
    initializedOperator = new DropTriggerOperator(SQLConstant.TOK_TRIGGER_DROP);
    operatorType = SQLConstant.TOK_TRIGGER_DROP;
    DropTriggerOperator operator = (DropTriggerOperator) initializedOperator;
    operator.setId(ctx.triggerName.getText());
  }

  @Override
  public void enterStartTrigger(StartTriggerContext ctx) {
    initializedOperator = new StartTriggerOperator(SQLConstant.TOK_TRIGGER_START);
    operatorType = SQLConstant.TOK_TRIGGER_START;
    StartTriggerOperator operator = (StartTriggerOperator) initializedOperator;
    operator.setId(ctx.triggerName.getText());
  }

  @Override
  public void enterStopTrigger(StopTriggerContext ctx) {
    initializedOperator = new StopTriggerOperator(SQLConstant.TOK_TRIGGER_STOP);
    operatorType = SQLConstant.TOK_TRIGGER_STOP;
    StopTriggerOperator operator = (StopTriggerOperator) initializedOperator;
    operator.setId(ctx.triggerName.getText());
  }

  @Override
  public void enterShowTriggers(ShowTriggersContext ctx) {
    initializedOperator = new ShowTriggersOperator(SQLConstant.TOK_TRIGGERS);
    operatorType = SQLConstant.TOK_TRIGGERS;
    ShowTriggersOperator showTriggersOperator = (ShowTriggersOperator) initializedOperator;
    if (ctx.fullPath() != null) {
      showTriggersOperator.setPath(parseFullPath(ctx.fullPath()).getFullPath());
    }
    if (ctx.SYNC() != null) {
      showTriggersOperator.setShowSyncTrigger(true);
      return;
    }
    if (ctx.ASYNC() != null) {
      showTriggersOperator.setShowAsyncTrigger(true);
      return;
    }
    showTriggersOperator.setShowSyncTrigger(true);
    showTriggersOperator.setShowAsyncTrigger(true);
  }

  private FilterOperator parseOrExpression(OrExpressionContext ctx) {
    if (ctx.andExpression().size() == 1) {
      return parseAndExpression(ctx.andExpression(0));
    }
    FilterOperator binaryOp = new FilterOperator(SQLConstant.KW_OR);
    if (ctx.andExpression().size() > 2) {
      binaryOp.addChildOperator(parseAndExpression(ctx.andExpression(0)));
      binaryOp.addChildOperator(parseAndExpression(ctx.andExpression(1)));
      for (int i = 2; i < ctx.andExpression().size(); i++) {
        FilterOperator op = new FilterOperator(SQLConstant.KW_OR);
        op.addChildOperator(binaryOp);
        op.addChildOperator(parseAndExpression(ctx.andExpression(i)));
        binaryOp = op;
      }
    } else {
      for (AndExpressionContext andExpressionContext : ctx.andExpression()) {
        binaryOp.addChildOperator(parseAndExpression(andExpressionContext));
      }
    }
    return binaryOp;
  }

  private FilterOperator parseAndExpression(AndExpressionContext ctx) {
    if (ctx.predicate().size() == 1) {
      return parsePredicate(ctx.predicate(0));
    }
    FilterOperator binaryOp = new FilterOperator(SQLConstant.KW_AND);
    int size = ctx.predicate().size();
    if (size > 2) {
      binaryOp.addChildOperator(parsePredicate(ctx.predicate(0)));
      binaryOp.addChildOperator(parsePredicate(ctx.predicate(1)));
      for (int i = 2; i < size; i++) {
        FilterOperator op = new FilterOperator(SQLConstant.KW_AND);
        op.addChildOperator(binaryOp);
        op.addChildOperator(parsePredicate(ctx.predicate(i)));
        binaryOp = op;
      }
    } else {
      for (PredicateContext predicateContext : ctx.predicate()) {
        binaryOp.addChildOperator(parsePredicate(predicateContext));
      }
    }
    return binaryOp;
  }

  private FilterOperator parsePredicate(PredicateContext ctx) {
    if (ctx.OPERATOR_NOT() != null) {
      FilterOperator notOp = new FilterOperator(SQLConstant.KW_NOT);
      notOp.addChildOperator(parseOrExpression(ctx.orExpression()));
      return notOp;
    } else if (ctx.LR_BRACKET() != null && ctx.OPERATOR_NOT() == null) {
      return parseOrExpression(ctx.orExpression());
    } else {
      Path path = null;
      if (ctx.TIME() != null || ctx.TIMESTAMP() != null) {
        path = new Path(SQLConstant.RESERVED_TIME);
      }
      if (ctx.fullPath() != null) {
        path = parseFullPath(ctx.fullPath());
      }
      if (ctx.suffixPath() != null) {
        path = parseSuffixPath(ctx.suffixPath());
      }
      if (path == null) {
        throw new SQLParserException("Path is null, please check the sql.");
      }
      if (ctx.inClause() != null) {
        return parseInOperator(ctx.inClause(), path);
      } else {
        return parseBasicFunctionOperator(ctx, path);
      }
    }
  }

  private FilterOperator parseInOperator(InClauseContext ctx, Path path) {
    Set<String> values = new HashSet<>();
    boolean not = ctx.OPERATOR_NOT() != null;
    for (ConstantContext constant : ctx.constant()) {
      if (constant.dateExpression() != null) {
        if (!path.equals(SQLConstant.RESERVED_TIME)) {
          throw new SQLParserException(path.toString(), "Date can only be used to time");
        }
        values.add(Long.toString(parseDateExpression(constant.dateExpression())));
      } else {
        values.add(constant.getText());
      }
    }
    return new InOperator(ctx.OPERATOR_IN().getSymbol().getType(), path, not, values);
  }

  private FilterOperator parseBasicFunctionOperator(PredicateContext ctx, Path path) {
    BasicFunctionOperator basic;
    if (ctx.constant().dateExpression() != null) {
      if (!path.equals(SQLConstant.RESERVED_TIME)) {
        throw new SQLParserException(path.toString(), "Date can only be used to time");
      }
      basic = new BasicFunctionOperator(ctx.comparisonOperator().type.getType(), path,
          Long.toString(parseDateExpression(ctx.constant().dateExpression())));
    } else {
      basic = new BasicFunctionOperator(ctx.comparisonOperator().type.getType(), path,
          ctx.constant().getText());
    }
    return basic;
  }

  private Path parseSuffixPath(SuffixPathContext ctx) {
    List<NodeNameContext> nodeNames = ctx.nodeName();
    List<String> path = new ArrayList<>();
    for (NodeNameContext nodeName : nodeNames) {
      path.add(nodeName.getText());
    }
    return new Path(
        new StringContainer(path.toArray(new String[0]), TsFileConstant.PATH_SEPARATOR));
  }

  /**
   * parse time expression, which is addition and subtraction expression of duration time, now() or
   * DataTimeFormat time.
   * <p>
   * eg. now() + 1d - 2h
   * </p>
   */
  private Long parseDateExpression(DateExpressionContext ctx) {
    long time;
    time = parseTimeFormat(ctx.getChild(0).getText());
    for (int i = 1; i < ctx.getChildCount(); i = i + 2) {
      if (ctx.getChild(i).getText().equals("+")) {
        time += parseDuration(ctx.getChild(i + 1).getText());
      } else {
        time -= parseDuration(ctx.getChild(i + 1).getText());
      }
    }
    return time;
  }

  /**
   * function for parsing time format.
   */
  long parseTimeFormat(String timestampStr) throws SQLParserException {
    if (timestampStr == null || timestampStr.trim().equals("")) {
      throw new SQLParserException("input timestamp cannot be empty");
    }
    long startupNano = IoTDBDescriptor.getInstance().getConfig().getStartUpNanosecond();
    if (timestampStr.equalsIgnoreCase(SQLConstant.NOW_FUNC)) {
      String timePrecision = IoTDBDescriptor.getInstance().getConfig().getTimestampPrecision();
      switch (timePrecision) {
        case "ns":
          return System.currentTimeMillis() * 1000_000 + (System.nanoTime() - startupNano) % 1000_000;
        case "us":
          return System.currentTimeMillis() * 1000 + (System.nanoTime() - startupNano) / 1000 % 1000;
        default:
          return System.currentTimeMillis();
      }
    }
    try {
      return DatetimeUtils.convertDatetimeStrToLong(timestampStr, zoneId);
    } catch (Exception e) {
      throw new SQLParserException(String
          .format("Input time format %s error. "
              + "Input like yyyy-MM-dd HH:mm:ss, yyyy-MM-ddTHH:mm:ss or "
              + "refer to user document for more info.", timestampStr));
    }
  }

  /**
   * for delete command, time should only have an end time.
   *
   * @param operator delete logical plan
   */
  private long parseDeleteTimeFilter(DeleteDataOperator operator) {
    FilterOperator filterOperator = operator.getFilterOperator();
    if (filterOperator.getTokenIntType() != SQLConstant.LESSTHAN
        && filterOperator.getTokenIntType() != SQLConstant.LESSTHANOREQUALTO) {
      throw new SQLParserException(
          "For delete command, where clause must be like : time < XXX or time <= XXX");
    }
    long time = Long.parseLong(((BasicFunctionOperator) filterOperator).getValue());
    if (filterOperator.getTokenIntType() == SQLConstant.LESSTHAN) {
      time = time - 1;
    }
    return time;
  }

  private void checkMetadataArgs(String dataType, String encoding, String compressor) {
    TSDataType tsDataType;
    TSEncoding tsEncoding;
    if (dataType == null) {
      throw new SQLParserException("data type cannot be null");
    }

    try {
      tsDataType = TSDataType.valueOf(dataType);
    } catch (Exception e) {
      throw new SQLParserException(String.format("data type %s not support", dataType));
    }

    if (encoding == null) {
      throw new SQLParserException("encoding type cannot be null");
    }

    try {
      tsEncoding = TSEncoding.valueOf(encoding);
    } catch (Exception e) {
      throw new SQLParserException(String.format("encoding %s is not support", encoding));
    }

    try {
      CompressionType.valueOf(compressor);
    } catch (Exception e) {
      throw new SQLParserException(String.format("compressor %s is not support", compressor));
    }

    checkDataTypeEncoding(tsDataType, tsEncoding);
  }

  private void checkDataTypeEncoding(TSDataType tsDataType, TSEncoding tsEncoding) {
    boolean throwExp = false;
    switch (tsDataType) {
      case BOOLEAN:
        if (!(tsEncoding.equals(TSEncoding.RLE) || tsEncoding.equals(TSEncoding.PLAIN))) {
          throwExp = true;
        }
        break;
      case INT32:
      case INT64:
        if (!(tsEncoding.equals(TSEncoding.RLE) || tsEncoding.equals(TSEncoding.PLAIN)
            || tsEncoding.equals(TSEncoding.TS_2DIFF))) {
          throwExp = true;
        }
        break;
      case FLOAT:
      case DOUBLE:
        if (!(tsEncoding.equals(TSEncoding.RLE) || tsEncoding.equals(TSEncoding.PLAIN)
            || tsEncoding.equals(TSEncoding.TS_2DIFF) || tsEncoding.equals(TSEncoding.GORILLA))) {
          throwExp = true;
        }
        break;
      case TEXT:
        if (!tsEncoding.equals(TSEncoding.PLAIN)) {
          throwExp = true;
        }
        break;
      default:
        throwExp = true;
    }
    if (throwExp) {
      throw new SQLParserException(
          String.format("encoding %s does not support %s", tsEncoding, tsDataType));
    }
  }
}
