package com.centit.dde.utils;

import com.alibaba.fastjson.JSON;
import com.centit.dde.core.DataSet;
import com.centit.dde.core.SimpleDataSet;
import com.centit.dde.datarule.CheckRule;
import com.centit.dde.datarule.CheckRuleUtils;
import com.centit.framework.components.CodeRepositoryUtil;
import com.centit.support.algorithm.*;
import com.centit.support.compiler.ObjectTranslate;
import com.centit.support.compiler.Pretreatment;
import com.centit.support.compiler.VariableFormula;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.commons.math3.stat.StatUtils;

import java.util.*;

/**
 * @author zhf
 */
public abstract class DataSetOptUtil {
    private static final String LEFT = "leftjoin";
    private static final String RIGHT = "rightjoin";
    private static final String ALL = "alljoin";

    public static VariableFormula createFormula(){
        VariableFormula formula = new VariableFormula();
        formula.addExtendFunc("toJson", (a) -> JSON.parse(
            StringBaseOpt.castObjectToString(a[0])));
        formula.addExtendFunc("uuid", (a) -> UuidOpt.getUuidAsString32());
        formula.addExtendFunc("dict", (a) -> a!=null && a.length>1 ?
            CodeRepositoryUtil.getValue(
                StringBaseOpt.castObjectToString(a[0]),
                StringBaseOpt.castObjectToString(a[1])) : null);
        return formula;
    }
    /**
     * 数据集 映射
     *
     * @param inRow      原始数据
     * @param formulaMap 字段映射关系
     * @return 新的数据集
     */
    static Map<String, Object> mapDataRow(Map<String, Object> inRow,
                                          Collection<Map.Entry<String, String>> formulaMap) {
        if (formulaMap == null) {
            return inRow;
        }
        VariableFormula formula = createFormula();
        formula.setTrans(new ObjectTranslate(inRow));
        Map<String, Object> newRow = new HashMap<>(formulaMap.size());
        for (Map.Entry<String, String> ent : formulaMap) {
            formula.setFormula(ent.getValue());
            newRow.put(ent.getKey(), formula.calcFormula());
        }
        return newRow;
    }

    /**
     * 数据集 映射
     *
     * @param inData     原始数据集
     * @param formulaMap 字段映射关系， value为计算表达式
     * @return 新的数据集
     */
    public static DataSet mapDateSetByFormula(DataSet inData, Collection<Map.Entry<String, String>> formulaMap) {
        List<Map<String, Object>> data = inData.getDataAsList();
        List<Map<String, Object>> newData = new ArrayList<>(data.size());
        for (Map<String, Object> obj : data) {
            newData.add(mapDataRow(obj, formulaMap));
        }
        return new SimpleDataSet(newData);
    }

    /**
     * 数据集 增加派生字段
     *
     * @param inData     原始数据集
     * @param formulaMap 字段映射关系， value为计算表达式
     */
    public static void appendDeriveField(DataSet inData, Collection<Map.Entry<String, String>> formulaMap) {
        List<Map<String, Object>> data = inData.getDataAsList();
        for (Map<String, Object> obj : data) {
            obj.putAll(mapDataRow(obj, formulaMap));
        }
    }

    /**
     * 数据集 映射
     *
     * @param inData   原始数据集
     * @param formulas 逻辑表达式
     * @return 新的数据集
     */
    public static DataSet filterDateSet(DataSet inData, List<String> formulas) {
        List<Map<String, Object>> data = inData.getDataAsList();
        List<Map<String, Object>> newData = new ArrayList<>(data.size());
        for (Map<String, Object> obj : data) {
            boolean canAdd = true;
            for (String formula : formulas) {
                if (!BooleanBaseOpt.castObjectToBoolean(
                    VariableFormula.calculate(formula, obj), false)) {
                    canAdd = false;
                    break;
                }
            }
            if (canAdd) {
                newData.add(obj);
            }
        }
        SimpleDataSet outDataSet = new SimpleDataSet(newData);
        outDataSet.setDataSetName(inData.getDataSetName());
        return outDataSet;
    }

    /**
     * 对数据集进行排序
     *
     * @param inData 原始数据集
     * @param fields 排序字段
     */
    public static void sortDataSetByFields(DataSet inData, List<String> fields) {
        sortByFields(inData.getDataAsList(), fields);
    }

    private static double[] listDoubleToArray(List<Double> dblist) {
        double[] dbs = new double[dblist.size()];
        int i = 0;
        for (Double db : dblist) {
            dbs[i++] = db != null ? db : 0.0;
        }
        return dbs;
    }

    private static Map<String, Object> makeNewStatRow(List<String> groupByFields,
                                                      List<Triple<String, String, String>> statDesc,
                                                      Map<String, Object> preRow,
                                                      Map<String, List<Double>> tempData) {
        Map<String, Object> newRow = new HashMap<>(groupByFields.size());
        if (groupByFields.size() > 0) {
            for (String field : groupByFields) {
                newRow.put(field, preRow.get(field));
            }
        }
        for (Triple<String, String, String> tr : statDesc) {
            double db;
            switch (tr.getRight()) {
                case "min":
                    db = StatUtils.min(listDoubleToArray(tempData.get(tr.getLeft())));
                    break;
                case "max":
                    db = StatUtils.max(listDoubleToArray(tempData.get(tr.getLeft())));
                    break;
                case "mean":
                    db = StatUtils.mean(listDoubleToArray(tempData.get(tr.getLeft())));
                    break;
                case "sum":
                    db = StatUtils.sum(listDoubleToArray(tempData.get(tr.getLeft())));
                    break;
                case "sumSq":
                    db = StatUtils.sumSq(listDoubleToArray(tempData.get(tr.getLeft())));
                    break;
                case "prod":
                    db = StatUtils.product(listDoubleToArray(tempData.get(tr.getLeft())));
                    break;
                case "sumLog":
                    db = StatUtils.sumLog(listDoubleToArray(tempData.get(tr.getLeft())));
                    break;
                case "geometricMean":
                    db = StatUtils.geometricMean(listDoubleToArray(tempData.get(tr.getLeft())));
                    break;
                case "variance":
                    db = StatUtils.variance(listDoubleToArray(tempData.get(tr.getLeft())));
                    break;
                /* percentile 这个没有实现*/
                default:
                    db = tempData.get(tr.getLeft()).size();
                    break;
            }
            newRow.put(tr.getLeft(), db);
        }
        return newRow;
    }

    public static DataSet statDataset(DataSet inData,
                                       List<String> groupByFields,
                                       Map<String, String> statDesc) {
        List<Triple<String, String, String>> sd = new ArrayList<>(statDesc.size());
        for (Map.Entry<String, String> s : statDesc.entrySet()) {
            String[] optDesc = StringUtils.split(s.getValue(), ":");
            if (optDesc != null && optDesc.length > 1) {
                sd.add(new MutableTriple<>(s.getKey(), optDesc[0], optDesc[1]));
            }
        }
        return statDataset(inData, groupByFields, sd);
    }

    /**
     * 分组统计 , 如果 List&gt;String&lt; groupbyFields 为null 或者 空 就是统计所有的（仅返回一行）
     *
     * @param inData        输入数据集
     * @param groupByFields 分组（排序）字段
     * @param statDesc      统计描述; 新字段名， 源字段名， 统计方式 （求和，最大，最小，平均，方差，标准差）
     * @return 返回数据集
     */
    private static DataSet statDataset(DataSet inData,
                                       List<String> groupByFields,
                                       List<Triple<String, String, String>> statDesc) {
        if (inData == null) {
            return null;
        }
        if (groupByFields == null) {
            return inData;
        }
        List<Map<String, Object>> data = inData.getDataAsList();
        if (data == null || data.size() == 0) {
            return inData;
        }
        //按group by字段排序
        if (groupByFields.size() > 0) {
            sortByFields(data, groupByFields);
        }

        List<Map<String, Object>> newData = new ArrayList<>();
        Map<String, Object> preRow = null;

        Map<String, List<Double>> tempData = new HashMap<>(statDesc.size());
        for (Triple<String, String, String> tr : statDesc) {
            tempData.put(tr.getLeft(), new ArrayList<>());
        }

        for (Map<String, Object> row : data) {
            if (0 != compareTwoRow(preRow, row, groupByFields)) {
                if (preRow != null) {
                    //保存newRow
                    Map<String, Object> newRow = makeNewStatRow(groupByFields,
                        statDesc, preRow, tempData);
                    newData.add(newRow);
                }
                // 新建数据临时数据空间
                for (Triple<String, String, String> tr : statDesc) {
                    tempData.get(tr.getLeft()).clear();
                }
            }
            for (Triple<String, String, String> tr : statDesc) {
                tempData.get(tr.getLeft()).add(
                    NumberBaseOpt.castObjectToDouble(row.get(tr.getMiddle())));
            }
            preRow = row;
        }

        if (preRow != null) {
            //保存newRow
            Map<String, Object> newRow = makeNewStatRow(groupByFields,
                statDesc, preRow, tempData);
            newData.add(newRow);
        }
        return new SimpleDataSet(newData);
    }

    /*public static DataSet sumRollupDataset(DataSet inData,
                                       List<String> groupbyFields, List<String> orderbyFields) {
        List<Map<String, Object>> data = inData.getDataAsList();
        List<String> keyRows = ListUtils.union(groupbyFields, orderbyFields);
        //根据维度进行排序 行头、列头
        sortByFields(data, keyRows);
        Map<String, Object> preRow = null;
        int n = data.size();
        return inData;
    }*/

    private static void analyseDatasetGroup(List<Map<String, Object>> newData,List<Map<String, Object>> data,
                                            int offset, int endPos,
                                            DatasetVariableTranslate dvt,
                                            Collection<Map.Entry<String, String>> refDesc) {
        dvt.setOffset(offset);
        dvt.setLength(endPos - offset);
        for (int j = offset; j < endPos; j++) {
            Map<String, Object> newRow = new HashMap<>(data.get(j));
            dvt.setCurrentPos(j);
            for (Map.Entry<String, String> ref : refDesc) {
                newRow.put(ref.getKey(),
                    VariableFormula.calculate(ref.getValue(), dvt));
            }
            newData.add(newRow);
        }
    }

    /**
     * 分组统计 , 如果 List&gt;String&lt; groupByFields 为null 或者 空 就是统计所有的（仅返回一行）
     *
     * @param inData        输入数据集
     * @param groupByFields 分组字段
     * @param orderByFields 排序字段
     * @param refDesc       引用说明; 新字段名， 引用表达式
     * @return 返回数据集
     */
    public static DataSet analyseDataset(DataSet inData,
                                         List<String> groupByFields,
                                         List<String> orderByFields,
                                         Collection<Map.Entry<String, String>> refDesc) {
        List<Map<String, Object>> data = inData.getDataAsList();
        List<String> keyRows = ListUtils.union(groupByFields, orderByFields);
        //根据维度进行排序 行头、列头
        sortByFields(data, keyRows);
        Map<String, Object> preRow = null;
        int n = data.size();
        int prePos = 0;
        DatasetVariableTranslate dvt = new DatasetVariableTranslate(data);
        List<Map<String, Object>> newData = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Map<String, Object> row = data.get(i);
            if (compareTwoRow(preRow, row, groupByFields) == 0) {
                if (preRow != null) {
                    analyseDatasetGroup(newData,data, prePos, i, dvt, refDesc);
                }
                prePos = i;
            }
            preRow = row;
        }
        analyseDatasetGroup(newData,data, prePos, n, dvt, refDesc);
        return new SimpleDataSet(newData);
    }

    /***
     * 交叉制表 数据处理
     * @param inData 输入数据集
     * @param colHeaderFields 列头信息
     * @param rowHeaderFields 行头信息
     * @return 输出数据集
     */
    public static DataSet crossTabulation(DataSet inData, List<String> rowHeaderFields, List<String> colHeaderFields) {
        if (inData == null) {
            return null;
        }
        List<Map<String, Object>> data = inData.getDataAsList();
        if (data == null || data.size() == 0) {
            return inData;
        }
        if (rowHeaderFields.size() + colHeaderFields.size() >= data.get(0).size()) {
            throw new RuntimeException("数据不合法");
        }
        List<String> keyRows = ListUtils.union(rowHeaderFields, colHeaderFields);
        //根据维度进行排序 行头、列头
        sortByFields(data, keyRows);
        List<Map<String, Object>> newData = new ArrayList<>();
        Map<String, Object> preRow = null;
        Map<String, Object> newRow = null;
        for (Map<String, Object> row : data) {
            if (row == null) {
                continue;
            }
            if (compareTwoRow(preRow, row, rowHeaderFields) != 0) {
                if (preRow != null && newRow != null) {
                    newData.add(newRow);
                }
                // 新建数据临时数据空间
                newRow = new HashMap<>(rowHeaderFields.size());
                for (String key : rowHeaderFields) {
                    newRow.put(key, row.get(key));
                }
            }
            StringBuilder colPrefix = new StringBuilder();
            for (String key : colHeaderFields) {
                colPrefix.append(key).append(":").append(row.get(key)).append(":");
            }
            String prefix = colPrefix.toString();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                if (!keyRows.contains(key)) {
                    if (newRow == null) {
                        newRow = new HashMap<>(row.size());
                    }
                    newRow.put(prefix + key, entry.getValue());
                }
            }
            preRow = row;
        }

        if (preRow != null && newRow != null) {
            newData.add(newRow);
        }
        return new SimpleDataSet(newData);
    }

    private static void appendData(Map<String, Object> newRow, Map<String, Object> oldData,
                                   List<String> primaryFields, String suffix, boolean appendKey) {

        for (Map.Entry<String, Object> entry : oldData.entrySet()) {
            String key = entry.getKey();
            if (primaryFields.contains(key)) {
                if (appendKey) {
                    newRow.put(key, entry.getValue());
                }
            } else {
                newRow.put(key + suffix, entry.getValue());
            }
        }
    }

    /**
     * 同环比转换
     *
     * @param currDataSet   本期数据集
     * @param lastDataSet   上期数据集
     * @param primaryFields 主键列
     * @return DataSet
     */
    public static DataSet compareTabulation(DataSet currDataSet,
                                            DataSet lastDataSet,
                                            List<Map.Entry<String, String>> primaryFields,
                                            Collection<Map.Entry<String, String>> formulaMap) {
        if (currDataSet == null || lastDataSet == null) {
            return null;
        }
        List<Map<String, Object>> currData = currDataSet.getDataAsList();
        List<Map<String, Object>> lastData = lastDataSet.getDataAsList();
        if (currData == null || lastData == null) {
            throw new RuntimeException("数据不合法");
        }

        List<Map<String, Object>> newData = new ArrayList<>();
        List<String> mainFields = new ArrayList<>();
        List<String> slaveFields = new ArrayList<>();
        splitMainAndSlave(primaryFields, mainFields, slaveFields);
        // 根据主键排序
        sortByFields(currData, mainFields);
        sortByFields(lastData, slaveFields);
        int i = 0;
        int j = 0;
        while (i < currData.size() && j < lastData.size()) {
            int nc = compareTwoRowWithMap(currData.get(i), lastData.get(j), primaryFields);
            //匹配
            Map<String, Object> newRow = new LinkedHashMap<>();
            if (nc == 0) {
                appendData(newRow, currData.get(i), mainFields, "_l", true);
                appendData(newRow, lastData.get(j), slaveFields, "_r", false);
                i++;
                j++;
            } else if (nc < 0) {
                appendData(newRow, currData.get(i), mainFields, "_l", true);
                i++;
            } else {
                appendData(newRow, lastData.get(j), slaveFields, "_r", true);
                j++;
            }
            newData.add(mapDataRow(newRow, formulaMap));
        }

        while (i < currData.size()) {
            Map<String, Object> newRow = new LinkedHashMap<>();
            appendData(newRow, currData.get(i), mainFields, "_l", true);
            newData.add(mapDataRow(newRow, formulaMap));
            i++;
        }

        while (j < lastData.size()) {
            Map<String, Object> newRow = new LinkedHashMap<>();
            appendData(newRow, lastData.get(j), slaveFields, "_r", true);
            newData.add(mapDataRow(newRow, formulaMap));
            j++;
        }
        return new SimpleDataSet(newData);
    }

    private static void splitMainAndSlave(List<Map.Entry<String, String>> primaryFields, List<String> mainFields, List<String> slaveFields) {
        for (Map.Entry<String, String> field : primaryFields) {
            mainFields.add(field.getKey());
            slaveFields.add(field.getValue());
        }
    }

    /**
     * 合并两个数据集
     *
     * @param mainDataSet   主数据集
     * @param slaveDataSet  次数据集
     * @param primaryFields 主键列
     * @return DataSet
     */
    public static DataSet joinTwoDataSet(DataSet mainDataSet, DataSet slaveDataSet, List<Map.Entry<String, String>> primaryFields, String join) {
        if (mainDataSet == null) {
            return slaveDataSet;
        }
        if (slaveDataSet == null) {
            return mainDataSet;
        }
        List<Map<String, Object>> mainData = mainDataSet.getDataAsList();
        List<Map<String, Object>> slaveData = slaveDataSet.getDataAsList();
        List<String> mainFields = new ArrayList<>();
        List<String> slaveFields = new ArrayList<>();
        splitMainAndSlave(primaryFields, mainFields, slaveFields);
        sortByFields(mainData, mainFields);
        sortByFields(slaveData, slaveFields);
        int i = 0;
        int j = 0;
        List<Map<String, Object>> newData = new ArrayList<>();
        int nInsertMain = -1;
        int nInsertSlave = -1;
        while (i < mainData.size() && j < slaveData.size()) {
            int nc = compareTwoRowWithMap(mainData.get(i), slaveData.get(j), primaryFields);
            Map<String, Object> newRow = new LinkedHashMap<>();
            if (nc == 0) {
                newRow.putAll(slaveData.get(j));
                newRow.putAll(mainData.get(i));
                nInsertMain = i;
                nInsertSlave = j;
                boolean incMain = i < mainData.size() - 1 && compareTwoRowWithMap(mainData.get(i), mainData.get(i + 1), primaryFields) != 0;
                boolean incSlave = j < slaveData.size() - 1 && compareTwoRowWithMap(slaveData.get(j), slaveData.get(j + 1), primaryFields) != 0;
                if (!incMain && i < mainData.size()) {
                    i++;
                }
                if (!incSlave && j < slaveData.size()) {
                    j++;
                }
                boolean inc = (incMain && incSlave) || (!incMain && !incSlave);
                if (inc) {
                    i++;
                    j++;
                }
            } else if (nc < 0) {
                if (nInsertMain < i) {
                    if (LEFT.equalsIgnoreCase(join) || ALL.equalsIgnoreCase(join)) {
                        newRow.putAll(mainData.get(i));
                    }
                    nInsertMain = i;
                }
                i++;
            } else {
                if (nInsertSlave < j) {
                    if (RIGHT.equalsIgnoreCase(join) || ALL.equalsIgnoreCase(join)) {
                        newRow.putAll(slaveData.get(j));
                    }
                    nInsertSlave = j;
                }
                j++;
            }
            if (newRow.size() > 0) {
                newData.add(newRow);
            }
        }
        if (LEFT.equalsIgnoreCase(join) || ALL.equalsIgnoreCase(join)) {
            while (i < mainData.size()) {
                Map<String, Object> newRow = new LinkedHashMap<>(mainData.get(i));
                newData.add(newRow);
                i++;
            }
        }
        if (RIGHT.equalsIgnoreCase(join) || ALL.equalsIgnoreCase(join)) {
            while (j < slaveData.size()) {
                Map<String, Object> newRow = new LinkedHashMap<>(slaveData.get(j));
                newData.add(newRow);
                j++;
            }
        }
        return new SimpleDataSet(newData);
    }

    public static DataSet filterByOtherDataSet(DataSet mainDataSet, DataSet slaveDataSet,
                                               List<Map.Entry<String, String>> primaryFields, List<String> formulas) {
        if (mainDataSet == null || slaveDataSet == null) {
            return null;
        }
        List<Map<String, Object>> mainData = mainDataSet.getDataAsList();
        List<Map<String, Object>> slaveData = slaveDataSet.getDataAsList();
        List<String> mainFields = new ArrayList<>();
        List<String> slaveFields = new ArrayList<>();
        splitMainAndSlave(primaryFields, mainFields, slaveFields);
        sortByFields(mainData, mainFields);
        sortByFields(slaveData, slaveFields);

        int i = 0;
        int j = 0;
        List<Map<String, Object>> newData = new ArrayList<>();
        // 根据主键排序
        while (i < mainData.size() && j < slaveData.size()) {
            int nc = compareTwoRowWithMap(mainData.get(i), slaveData.get(j), primaryFields);
            //匹配
            Map<String, Object> newRow = new LinkedHashMap<>();
            if (nc == 0) {
                boolean canAdd = true;
                for (String formula : formulas) {
                    if (!BooleanBaseOpt.castObjectToBoolean(
                        VariableFormula.calculate(formula, slaveData.get(j)), false)) {
                        canAdd = false;
                        break;
                    }
                }
                if (canAdd){
                    newRow.putAll(mainData.get(i));
                }
                i++;
                j++;
            } else if (nc < 0) {
                i++;
            } else {
                j++;
            }
            if(newRow.size()!=0) {
                newData.add(newRow);
            }
        }
        return new SimpleDataSet(newData);
    }

    public static DataSet unionTwoDataSet(DataSet mainDataSet, DataSet slaveDataSet) {

        List<Map<String, Object>> mainData = mainDataSet.getDataAsList();
        List<Map<String, Object>> slaveData = slaveDataSet.getDataAsList();
        if (mainData == null) {
            return new SimpleDataSet(slaveData);
        }
        if (slaveData == null) {
            return new SimpleDataSet(mainData);
        }
        List<Map<String, Object>> resultData = new ArrayList<>(mainData.size() + slaveData.size());
        resultData.addAll(mainData);
        resultData.addAll(slaveData);
        return new SimpleDataSet(resultData);
    }

    public static void checkDateSet(DataSet inData, Collection<CheckRule> rules) {
        List<Map<String, Object>> data = inData.getDataAsList();

        for (Map<String, Object> obj : data) {
            StringBuilder checkResult = new StringBuilder();
            for (CheckRule rule : rules) {
                if (!CheckRuleUtils.checkData(obj, rule)) {
                    checkResult.append(Pretreatment.mapTemplateString(rule.getErrormsg(), obj)).append(";");
                }
            }
            if (StringUtils.isBlank(checkResult.toString())) {
                checkResult = new StringBuilder("ok");
            }
            obj.put(CheckRuleUtils.CHECK_RULE_RESULT_TAG, checkResult.toString());
        }
    }

    private static int compareTwoRow(Map<String, Object> data1, Map<String, Object> data2, List<String> fields) {
        if (data1 == null && data2 == null) {
            return 0;
        }
        if (data1 == null) {
            return -1;
        }
        if (data2 == null) {
            return 1;
        }
        if (fields == null) {
            return 0;
        }
        for (String field : fields) {
            if (field.endsWith(" desc")) {
                String dataField = field.substring(0, field.length() - 5).trim();
                int cr = GeneralAlgorithm.compareTwoObject(
                    data1.get(dataField), data2.get(dataField));
                if (cr != 0) {
                    return 0 - cr;
                }
            } else {
                int cr = GeneralAlgorithm.compareTwoObject(
                    data1.get(field), data2.get(field));
                if (cr != 0) {
                    return cr;
                }
            }
        }
        return 0;
    }

    private static int compareTwoRowWithMap(Map<String, Object> data1, Map<String, Object> data2, List<Map.Entry<String, String>> fields) {
        if (data1 == null && data2 == null) {
            return 0;
        } else if (fields == null) {
            return 0;
        }

        if (data1 == null) {
            return -1;
        }

        if (data2 == null) {
            return 1;
        }

        for (Map.Entry<String, String> field : fields) {
            int cr = GeneralAlgorithm.compareTwoObject(
                data1.get(field.getKey()), data2.get(field.getValue()));
            if (cr != 0) {
                return cr;
            }
        }
        return 0;
    }

    private static void sortByFields(List<Map<String, Object>> data, List<String> fields) {
        data.sort((o1, o2) -> compareTwoRow(o1, o2, fields));
    }
}
