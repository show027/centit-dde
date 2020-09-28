package com.centit.dde.services.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.centit.product.dataopt.core.BizModel;
import com.centit.product.dataopt.core.BizOperation;
import com.centit.product.dataopt.core.BizOptFlow;
import com.centit.product.dataopt.core.BizSupplier;
import com.centit.product.dataopt.utils.BuiltInOperation;
import com.centit.support.common.ObjectException;
import com.centit.support.database.transaction.ConnectThreadHolder;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * 业务流
 */
@Service
public class BizOptFlowImpl implements BizOptFlow {

    public Map<String, BizOperation> allOperations;

    public BizOptFlowImpl(){
        allOperations = new HashMap<>(50);
        allOperations.put("map", BuiltInOperation::runMap);
        allOperations.put("filter", BuiltInOperation::runFilter);
        allOperations.put("append", BuiltInOperation::runAppend);
        allOperations.put("stat", BuiltInOperation::runStat);
        allOperations.put("analyse", BuiltInOperation::runAnalyse);
        allOperations.put("cross", BuiltInOperation::runCross);
        allOperations.put("compare", BuiltInOperation::runCompare);
        allOperations.put("sort", BuiltInOperation::runSort);
        allOperations.put("join", BuiltInOperation::runJoin);
        allOperations.put("union", BuiltInOperation::runUnion);
        allOperations.put("filterExt", BuiltInOperation::runFilterExt);
        allOperations.put("check", BuiltInOperation::runCheckData);
        allOperations.put("static", BuiltInOperation::runStaticData);
        //allOperations.put("js", BuiltInOperation::runJsData);
        allOperations.put("http", BuiltInOperation::runHttpData);
    }

    @Override
    public void registOperation(String key, BizOperation opt){
        allOperations.put(key, opt);
    }
    /**
     * @return 返回真正运行的次数, 如果小于 0 表示報錯
     */
    @Override
    public BizModel run(BizSupplier supplier, JSONObject bizOptJson){
        //int n = 0;
        BizModel result =null;
        do{
            BizModel tempBM = supplier.get();
            if(tempBM == null || tempBM.isEmpty()){
                break;
            }
            //n ++;
            result = apply(tempBM, bizOptJson);
         } while(supplier.isBatchWise());
        return result;
    }


    protected void runOneStep(BizModel bizModel, JSONObject bizOptJson) {
        String sOptType = bizOptJson.getString("operation");
        BizOperation opt = allOperations.get(sOptType);
        if(opt == null) {
            throw new ObjectException(bizOptJson, "找不到对应的操作："+sOptType);
        }
        opt.doOpt(bizModel, bizOptJson);
    }

    @SneakyThrows
    public BizModel apply(BizModel bizModel, JSONObject bizOptJson) {
        JSONArray optSteps = bizOptJson.getJSONArray("steps");
        if (optSteps == null || optSteps.isEmpty()) {
            return bizModel;
        }
        try {
            for (Object step : optSteps) {
                if (step instanceof JSONObject) {
                    /*result =*/
                    runOneStep(bizModel, (JSONObject) step);
                }
            }
            ConnectThreadHolder.commitAndRelease();
        }catch (SQLException e){
            ConnectThreadHolder.rollbackAndRelease();
            throw e;
        }
        return bizModel;
    }

}
