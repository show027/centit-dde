package com.centit.dde.utils;

import com.centit.dde.core.BizModel;
import com.centit.dde.core.DataSet;
import com.centit.support.algorithm.ReflectionOpt;
import com.centit.support.compiler.VariableFormula;
import com.centit.support.compiler.VariableTranslate;
import com.centit.support.json.JSONTransformDataSupport;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zhf
 */
public class BizModelJSONTransform
    implements VariableTranslate, JSONTransformDataSupport{
    private BizModel data;
    private int stackLength;
    private List<Object> stack;

    public BizModelJSONTransform(BizModel obj){
        this.data = obj;
        this.stackLength = 0;
        this.stack = new ArrayList<>(10);
    }

    @Override
    public Object attainExpressionValue(String expression){
        return VariableFormula.calculate(expression, this);
    }

    private Object peekStackValue(){
        return stackLength> 1 ? stack.get(stackLength-2): null;
    }

    @Override
    public void pushStackValue(Object value){
        if(stack.size()>stackLength){
            stack.set(stackLength, value);
        } else {
            stack.add(value);
        }
        stackLength++;
    }

    @Override
    public Object popStackValue(){
        Object obj = stackLength>0? stack.get(stackLength-1): null;
        if(stackLength>0){
            stackLength-- ;
        }
        return obj;
    }

    @Override
    public Object getVarValue(String labelName) {
        if(labelName.startsWith(Constant.BACKSLASH)){
            return fetchRootData(labelName.substring(1));
        } else if(labelName.startsWith(Constant.DOUBLE_SPOT)){
            return ReflectionOpt.attainExpressionValue(
                peekStackValue(),
                labelName.substring(2));
        } else if(labelName.startsWith(Constant.SPOT)){
            if(stackLength>0) {
                return ReflectionOpt.attainExpressionValue(
                    stack.get(stackLength-1),
                    labelName.substring(1));
            } else {
                return fetchRootData(labelName.substring(1));
            }
        } else {
            if(stackLength>0) {
                return ReflectionOpt.attainExpressionValue(
                    stack.get(stackLength-1),
                    labelName);
            } else {
                return fetchRootData(labelName);
            }
        }
    }

    private Object fetchRootData(String labelName){
        if(StringUtils.isBlank(labelName)){
            return null;
        }

        String dataSetName , valuePath;
        int pointIndex = labelName.indexOf('.');
        int pointIndex2 = labelName.indexOf('[');
        if(pointIndex2>0 && pointIndex2<pointIndex){
            pointIndex = pointIndex2;
        }
        if(pointIndex>0){
            dataSetName = labelName.substring(0, pointIndex);
            valuePath = labelName.substring(pointIndex+1);
        } else {
            dataSetName = labelName;
            valuePath = null;
        }

        DataSet currentDateSet = this.data.fetchDataSetByName(dataSetName);
        if(currentDateSet!=null){
            if(StringUtils.isBlank(valuePath)){
                return currentDateSet.getDataAsList();
            }
            return ReflectionOpt.attainExpressionValue(
                currentDateSet.getDataAsList(),
                valuePath);
        }

        if(Constant.MODEL_TAG.equals(dataSetName)){
            if(StringUtils.isBlank(valuePath)) {
                return this.data.getModelTag();
            } else {
                if(this.data.getModelTag().containsKey(valuePath)) {
                    return this.data.getModelTag().get(valuePath);
                }
                if(this.data.getModelTag().containsKey(labelName)) {
                    return this.data.getModelTag().get(labelName);
                }
            }
        }
        if(Constant.MODEL_NAME.equals(dataSetName)){
            return this.data.getModelName();
        }
        return null;
    }

}
