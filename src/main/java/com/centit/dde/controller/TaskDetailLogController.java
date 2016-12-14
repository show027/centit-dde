package com.centit.dde.controller;

import java.util.List;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.centit.dde.po.TaskDetailLog;
import com.centit.dde.po.TaskErrorData;
import com.centit.dde.service.ExchangeMapinfoManager;
import com.centit.dde.service.TaskDetailLogManager;
import com.centit.framework.core.common.JsonResultUtils;
import com.centit.framework.core.controller.BaseController;
import com.sun.istack.Nullable;

@Controller
@RequestMapping("/TaskDetailLog")
public class TaskDetailLogController extends BaseController {
    private static final Log log = LogFactory.getLog(TaskDetailLogController.class);

    //private static final ISysOptLog sysOptLog = SysOptLogFactoryImpl.getSysOptLog("optid");

    private static final long serialVersionUID = 1L;
    @Resource
    @Nullable
    private TaskDetailLogManager taskDetailLogMag;
    @Resource
    @Nullable
    private ExchangeMapinfoManager exchangeMapinfoManager;

    private List<TaskErrorData> taskErrorDatas;

    public List<TaskErrorData> getNewTaskErrorDatas() {
        return this.taskErrorDatas;
    }

    public void setNewTaskErrorDatas(List<TaskErrorData> taskErrorDatas) {
        this.taskErrorDatas = taskErrorDatas;
    }


    @RequestMapping(value="edit",method = {RequestMethod.PUT})
    public void edit(TaskDetailLog object ,HttpServletRequest request,HttpServletResponse response) {
        try {
            if (object == null) {
//                object = getEntityClass().newInstance();
            } else {
                TaskDetailLog o = taskDetailLogMag.getObjectById(object.getLogDetailId());
                if (o != null)
                    // 将对象o copy给object，object自己的属性会保留
                    taskDetailLogMag.copyObject(object, o);
                else
                    taskDetailLogMag.clearObjectProperties(object);
            }
            if (null != object.getMapinfoId()) {
//                ServletActionContext.getContext().put("exchangeMapinfo", exchangeMapinfoManager.getObjectById(object.getMapinfoId()));
            }
//            return EDIT;
            JsonResultUtils.writeSingleDataJson(object, response);
        } catch (Exception e) {
            e.printStackTrace();
//            return ERROR;
            JsonResultUtils.writeErrorMessageJson("error", response);
        }
    }

    @RequestMapping(value="save",method = {RequestMethod.PUT})
    public void save(TaskDetailLog object ,HttpServletRequest request,HttpServletResponse response) {
        object.replaceTaskErrorDatas(taskErrorDatas);
//        super.save();
        taskDetailLogMag.saveObject(object);
        
//        return super.save();
        JsonResultUtils.writeSuccessJson(response);
    }

    @RequestMapping(value="delete",method = {RequestMethod.DELETE})
    public void delete(TaskDetailLog object ,HttpServletRequest request,HttpServletResponse response) {
        taskDetailLogMag.deleteObjectById(object.getLogDetailId());

//        return "delete";
        JsonResultUtils.writeSuccessJson(response);
    }
}
