package com.xxl.job.admin.service.impl;

import com.xxl.job.admin.controller.JobApiController;
import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.model.XxlJobLog;
import com.xxl.job.admin.core.schedule.XxlJobDynamicScheduler;
import com.xxl.job.admin.core.trigger.XxlJobTrigger;
import com.xxl.job.admin.core.util.JobKeyUtil;
import com.xxl.job.admin.dao.XxlJobInfoDao;
import com.xxl.job.admin.dao.XxlJobLogDao;
import com.xxl.job.admin.dao.XxlJobRegistryDao;
import com.xxl.job.admin.service.XxlJobService;
import com.xxl.job.core.biz.AdminBiz;
import com.xxl.job.core.biz.model.HandleCallbackParam;
import com.xxl.job.core.biz.model.RegistryParam;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.IJobHandler;
import org.apache.commons.lang3.StringUtils;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;

/**
 * @author xuxueli 2017-07-27 21:54:20
 */
@Service
public class AdminBizImpl implements AdminBiz {
    private static Logger logger = LoggerFactory.getLogger(AdminBizImpl.class);

    @Resource
    public XxlJobLogDao xxlJobLogDao;
    @Resource
    private XxlJobInfoDao xxlJobInfoDao;
    @Resource
    private XxlJobRegistryDao xxlJobRegistryDao;
    @Resource
    private XxlJobService xxlJobService;


    @Override
    public ReturnT<String> callback(List<HandleCallbackParam> callbackParamList) {
        for (HandleCallbackParam handleCallbackParam: callbackParamList) {
            ReturnT<String> callbackResult = callback(handleCallbackParam);
            logger.info(">>>>>>>>> JobApiController.callback {}, handleCallbackParam={}, callbackResult={}",
                    (callbackResult.getCode()==IJobHandler.SUCCESS.getCode()?"success":"fail"), handleCallbackParam, callbackResult);
        }

        return ReturnT.SUCCESS;
    }

    private ReturnT<String> callback(HandleCallbackParam handleCallbackParam) {
        // valid log item
        XxlJobLog log = xxlJobLogDao.load(handleCallbackParam.getLogId());
        if (log == null) {
            return new ReturnT<String>(ReturnT.FAIL_CODE, "log item not found.");
        }
        if (log.getHandleCode() > 0) {
            return new ReturnT<String>(ReturnT.FAIL_CODE, "log repeate callback.");     // avoid repeat callback, trigger child job etc
        }

        // trigger success, to trigger child job
        String callbackMsg = null;
        if (IJobHandler.SUCCESS.getCode() == handleCallbackParam.getExecuteResult().getCode()) {
            XxlJobInfo xxlJobInfo = xxlJobInfoDao.loadById(log.getJobId());
            if (xxlJobInfo!=null && StringUtils.isNotBlank(xxlJobInfo.getChildJobKey())) {
                callbackMsg = "<br><br><span style=\"color:#00c0ef;\" > >>>>>>>>>>>触发子任务<<<<<<<<<<< </span><br>";

                String[] childJobKeys = xxlJobInfo.getChildJobKey().split(",");
                for (int i = 0; i < childJobKeys.length; i++) {
                    int childJobId = JobKeyUtil.parseJobId(childJobKeys[i]);
                    if (childJobId > 0) {
                        ReturnT<String> triggerChildResult = xxlJobService.triggerJob(childJobId);

                        // add msg
                        callbackMsg += MessageFormat.format("{0}/{1} [JobKey={2}], 触发{3}, 触发备注: {4} <br>",
                                (i+1), childJobKeys.length, childJobKeys[i], (triggerChildResult.getCode()==ReturnT.SUCCESS_CODE?"成功":"失败"), triggerChildResult.getMsg());
                    } else {
                        callbackMsg += MessageFormat.format(" {0}/{1} [JobKey={2}], 触发失败, 触发备注: JobKey格式错误 <br>",
                                (i+1), childJobKeys.length, childJobKeys[i]);
                    }
                }

            }
        } else if (IJobHandler.FAIL_RETRY.getCode() == handleCallbackParam.getExecuteResult().getCode()){
            ReturnT<String> retryTriggerResult = xxlJobService.triggerJob(log.getJobId());
            callbackMsg = "<br><br><span style=\"color:#F39C12;\" > >>>>>>>>>>>执行失败重试<<<<<<<<<<< </span><br>";

            callbackMsg += MessageFormat.format("触发{0}, 触发备注: {1}",
                   (retryTriggerResult.getCode()==ReturnT.SUCCESS_CODE?"成功":"失败"), retryTriggerResult.getMsg());
        }

        // handle msg
        StringBuffer handleMsg = new StringBuffer();
        if (log.getHandleMsg()!=null) {
            handleMsg.append(log.getHandleMsg()).append("<br>");
        }
        if (handleCallbackParam.getExecuteResult().getMsg() != null) {
            handleMsg.append(handleCallbackParam.getExecuteResult().getMsg());
        }
        if (callbackMsg != null) {
            handleMsg.append(callbackMsg);
        }

        // success, save log
        log.setHandleTime(new Date());
        log.setHandleCode(handleCallbackParam.getExecuteResult().getCode());
        log.setHandleMsg(handleMsg.toString());
        xxlJobLogDao.updateHandleInfo(log);

        return ReturnT.SUCCESS;
    }

    @Override
    public ReturnT<String> registry(RegistryParam registryParam) {
        int ret = xxlJobRegistryDao.registryUpdate(registryParam.getRegistGroup(), registryParam.getRegistryKey(), registryParam.getRegistryValue());
        if (ret < 1) {
            xxlJobRegistryDao.registrySave(registryParam.getRegistGroup(), registryParam.getRegistryKey(), registryParam.getRegistryValue());
        }
        return ReturnT.SUCCESS;
    }

    @Override
    public ReturnT<String> registryRemove(RegistryParam registryParam) {
        xxlJobRegistryDao.registryDelete(registryParam.getRegistGroup(), registryParam.getRegistryKey(), registryParam.getRegistryValue());
        return ReturnT.SUCCESS;
    }

    @Override
    public ReturnT<String> triggerJob(int jobId) {
        return xxlJobService.triggerJob(jobId);
    }

}
