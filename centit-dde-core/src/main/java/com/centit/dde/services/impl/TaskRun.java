package com.centit.dde.services.impl;

import com.alibaba.fastjson.JSONObject;
import com.centit.dde.core.BizModel;
import com.centit.dde.core.BizOptFlow;
import com.centit.dde.core.DataSet;
import com.centit.dde.dao.DataPacketDao;
import com.centit.dde.dao.TaskDetailLogDao;
import com.centit.dde.dao.TaskLogDao;
import com.centit.dde.dataset.SQLDataSetWriter;
import com.centit.dde.po.DataPacket;
import com.centit.dde.po.TaskDetailLog;
import com.centit.dde.po.TaskLog;
import com.centit.dde.services.DBPacketBizSupplier;
import com.centit.fileserver.common.FileStore;
import com.centit.framework.ip.service.IntegrationEnvironment;
import com.centit.framework.jdbc.dao.DatabaseOptUtils;
import com.centit.support.algorithm.StringBaseOpt;
import com.centit.support.algorithm.UuidOpt;
import com.centit.support.common.ObjectException;
import com.centit.support.database.utils.FieldType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.support.CronSequenceGenerator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author zhf
 */
@Service
public class TaskRun {
    private final TaskLogDao taskLogDao;
    private final TaskDetailLogDao taskDetailLogDao;
    private final DataPacketDao dataPacketDao;
    private final IntegrationEnvironment integrationEnvironment;

    @Autowired(required = false)
    public void setBizOptFlow(BizOptFlow bizOptFlow) {
        this.bizOptFlow = bizOptFlow;
    }

    private BizOptFlow bizOptFlow;
    private FileStore fileStore;

    @Autowired(required = false)
    public void setFileStore(FileStore fileStore) {
        this.fileStore = fileStore;
    }


    @Autowired
    public TaskRun(TaskLogDao taskLogDao,
                   TaskDetailLogDao taskDetailLogDao,
                   DataPacketDao dataPacketDao,
                   IntegrationEnvironment integrationEnvironment) {
        this.taskLogDao = taskLogDao;
        this.taskDetailLogDao = taskDetailLogDao;
        this.dataPacketDao = dataPacketDao;
        this.integrationEnvironment = integrationEnvironment;
    }

    private BizModel runStep(DataPacket dataPacket) throws Exception {
        JSONObject bizOptJson = dataPacket.getDataOptDescJson();
        if (bizOptJson.isEmpty()) {
            return null;
        }
        DBPacketBizSupplier dbPacketBizSupplier = new DBPacketBizSupplier(dataPacket);
        dbPacketBizSupplier.setIntegrationEnvironment(integrationEnvironment);
        dbPacketBizSupplier.setFileStore(fileStore);
        dbPacketBizSupplier.setBatchWise(dataPacket.getIsWhile());
        /*添加参数默认值传输*/
        dbPacketBizSupplier.setQueryParams(dataPacket.getPacketParamsValue());
        return bizOptFlow.run(dbPacketBizSupplier, bizOptJson);
    }


    private Boolean saveDetail(BizModel iResult, String info,TaskLog taskLog,Date beginTime) {
        List<TaskDetailLog> taskDetailLogs = new ArrayList<>();
        boolean result=true;
        if (iResult != null) {
            for (DataSet dataset : iResult.getBizData().values()) {
                TaskDetailLog detailLog = new TaskDetailLog();
                detailLog.setRunBeginTime(beginTime);
                detailLog.setTaskId(taskLog.getTaskId());
                detailLog.setLogId(taskLog.getLogId());
                detailLog.setSuccessPieces(0L);
                detailLog.setErrorPieces(0L);
                StringBuilder msg = new StringBuilder();
                if (dataset.getData() != null) {
                    msg.append(dataset.getDataSetName()).append(":");
                    msg.append(dataset.size()).append("nums");
                    if (dataset.size() > 0) {
                        String error = (String) dataset.getData().get(0).get(FieldType.mapPropName(SQLDataSetWriter.WRITER_ERROR_TAG));
                        msg.append(error);
                        if ("ok".equalsIgnoreCase(error)) {
                            detailLog.setSuccessPieces((long) dataset.size());
                            result=true;
                        } else {
                            detailLog.setErrorPieces((long) dataset.size());
                            result=false;
                        }
                    }
                    msg.append(";");
                }
                detailLog.setLogType(info);
                detailLog.setLogInfo(msg.toString());
                detailLog.setRunEndTime(new Date());
                detailLog.setLogDetailId(UuidOpt.getUuidAsString32());
                taskDetailLogs.add(detailLog);
            }
        } else {
            TaskDetailLog detailLog = new TaskDetailLog();
            detailLog.setRunBeginTime(beginTime);
            detailLog.setTaskId(taskLog.getTaskId());
            detailLog.setLogId(taskLog.getLogId());
            detailLog.setLogType("error");
            detailLog.setLogInfo(info);
            detailLog.setRunEndTime(new Date());
            detailLog.setLogDetailId(UuidOpt.getUuidAsString32());
            taskDetailLogs.add(detailLog);
            result=false;
        }
        DatabaseOptUtils.batchSaveNewObjects(taskDetailLogDao, taskDetailLogs);
        return result;
    }

    public void runTask(String packetId) {
        BizModel bizModel=null;
        TaskLog taskLog = new TaskLog();
        Date beginTime = new Date();
        Boolean result=false;
        try {
            DataPacket dataPacket = dataPacketDao.getObjectWithReferences(packetId);
            dataPacket.setLastRunTime(new Date());
            taskLog.setTaskId(packetId);
            taskLog.setApplicationId(dataPacket.getApplicationId());
            taskLog.setRunBeginTime(beginTime);
            taskLog.setRunType(dataPacket.getPacketName());
            taskLog.setLogId(UuidOpt.getUuidAsString32());
            bizModel = runStep(dataPacket);
            taskLog.setRunEndTime(new Date());
            dataPacket.setNextRunTime(new Date());
            if ("2".equals(dataPacket.getTaskType())
                && dataPacket.getIsValid()
                && !StringBaseOpt.isNvl(dataPacket.getTaskCron())) {
                CronSequenceGenerator cronSequenceGenerator = new CronSequenceGenerator(dataPacket.getTaskCron());
                dataPacket.setNextRunTime(cronSequenceGenerator.next(dataPacket.getLastRunTime()));
            }
            dataPacketDao.updateObject(dataPacket);
            result=saveDetail(bizModel,"ok",taskLog,beginTime);
            taskLog.setOtherMessage(result?"ok":"error");
            taskLogDao.saveNewObject(taskLog);
        }catch (Exception e){
            saveDetail(bizModel, ObjectException.extortExceptionMessage(e, 4),
                taskLog, beginTime);
            taskLog.setOtherMessage("error");
            taskLogDao.updateObject(taskLog);
        }
    }
}
