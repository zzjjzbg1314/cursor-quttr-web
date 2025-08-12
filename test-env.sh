#!/bin/bash

echo "=== MQTT环境变量测试脚本 ==="
echo ""

# 检查关键环境变量
echo "1. 检查关键环境变量:"
echo "   MQTT_ACCESS_KEY: '${MQTT_ACCESS_KEY:-NOT_SET}'"
echo "   MQTT_SECRET_KEY: '${MQTT_SECRET_KEY:-NOT_SET}'"
echo "   MQTT_INSTANCE_ID: '${MQTT_INSTANCE_ID:-NOT_SET}'"
echo "   MQTT_ENDPOINT: '${MQTT_ENDPOINT:-NOT_SET}'"
echo ""

# 检查环境变量长度
echo "2. 检查环境变量长度:"
echo "   MQTT_ACCESS_KEY长度: ${#MQTT_ACCESS_KEY:-0}"
echo "   MQTT_SECRET_KEY长度: ${#MQTT_SECRET_KEY:-0}"
echo "   MQTT_INSTANCE_ID长度: ${#MQTT_INSTANCE_ID:-0}"
echo ""

# 检查环境变量是否为空
echo "3. 检查环境变量是否为空:"
if [ -z "$MQTT_ACCESS_KEY" ]; then
    echo "   ❌ MQTT_ACCESS_KEY 为空"
else
    echo "   ✅ MQTT_ACCESS_KEY 已设置"
fi

if [ -z "$MQTT_SECRET_KEY" ]; then
    echo "   ❌ MQTT_SECRET_KEY 为空"
else
    echo "   ✅ MQTT_SECRET_KEY 已设置"
fi

if [ -z "$MQTT_INSTANCE_ID" ]; then
    echo "   ❌ MQTT_INSTANCE_ID 为空"
else
    echo "   ✅ MQTT_INSTANCE_ID 已设置"
fi
echo ""

# 显示所有MQTT相关的环境变量
echo "4. 所有MQTT相关环境变量:"
env | grep -i mqtt | sort
echo ""

# 测试Spring Boot是否能读取环境变量
echo "5. 测试Spring Boot环境变量读取:"
echo "   启动应用后查看控制台输出的环境变量检查信息"
echo ""

echo "=== 测试完成 ==="
