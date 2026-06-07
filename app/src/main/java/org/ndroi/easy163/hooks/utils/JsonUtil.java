package org.ndroi.easy163.hooks.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * JSON 递归遍历工具类。
 * 支持对 JSONObject/JSONArray 树结构的深度优先遍历，
 * 通过 {@link Rule} 回调接口对每个 JSONObject 节点执行自定义操作。
 *
 * @author ndroi
 */
public class JsonUtil
{
    /**
     * JSON 遍历回调规则接口。
     * 定义对遍历过程中每个 JSONObject 节点的操作逻辑。
     */
    public interface Rule
    {
        /**
         * 对当前遍历到的 JSONObject 节点执行操作。
         *
         * @param object 当前遍历到的 JSONObject 节点
         */
        void apply(JSONObject object);
    }

    /**
     * 递归遍历 JSON 对象树，对每个 JSONObject 节点应用指定规则。
     * 采用深度优先遍历策略：遇到 JSONObject 时先应用规则再遍历子节点，
     * 遇到 JSONArray 时遍历数组中的每个元素。
     *
     * @param object 待遍历的 JSON 对象（可以是 JSONObject 或 JSONArray）
     * @param rule   对每个 JSONObject 节点执行的回调规则
     */
    public static void traverse(Object object, Rule rule)
    {
        if (object == null || rule == null)
        {
            return;
        }
        if (object.getClass().equals(JSONObject.class))
        {
            JSONObject jsonObject = (JSONObject) object;
            rule.apply(jsonObject);
            for (Object subObject : jsonObject.values())
            {
                traverse(subObject, rule);
            }
        } else if (object.getClass().equals(JSONArray.class))
        {
            JSONArray jsonArray = (JSONArray) object;
            for (Object subObject : jsonArray)
            {
                traverse(subObject, rule);
            }
        }
    }
}
