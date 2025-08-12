#!/bin/bash

echo "=== IntelliJ IDEA MQTT环境变量配置脚本 ==="
echo ""

# 检查是否在项目根目录
if [ ! -f "pom.xml" ]; then
    echo "❌ 请在项目根目录运行此脚本"
    exit 1
fi

echo "✅ 检测到Maven项目"
echo ""

# 显示需要配置的环境变量
echo "请在IntelliJ IDEA中配置以下环境变量："
echo ""
echo "1. 打开 Run/Debug Configurations"
echo "2. 选择或创建Spring Boot配置"
echo "3. 在Environment variables中添加："
echo ""

# 生成环境变量配置
cat << 'EOF'
MQTT_INSTANCE_ID=your_instance_id_here
MQTT_ENDPOINT=your_instance_id_here.mqtt.aliyuncs.com
MQTT_ACCESS_KEY=你的真实AccessKey
MQTT_SECRET_KEY=你的真实SecretKey
MQTT_GROUP_ID=GID_QUITTR
MQTT_DEVICE_ID=your-device-id
MQTT_PARENT_TOPIC=CHAT_OFFICIAL_GROUP
MQTT_QOS_LEVEL=0
MQTT_TCP_PORT=1883
EOF

echo ""
echo "4. 点击Apply和OK保存配置"
echo "5. 运行应用测试MQTT连接"
echo ""

# 检查当前环境变量
echo "当前系统环境变量检查："
echo "MQTT_ACCESS_KEY: ${MQTT_ACCESS_KEY:-NOT_SET}"
echo "MQTT_SECRET_KEY: ${MQTT_SECRET_KEY:-NOT_SET}"
echo "MQTT_INSTANCE_ID: ${MQTT_INSTANCE_ID:-NOT_SET}"
echo ""

echo "=== 配置说明完成 ==="
echo "请按照上述步骤在IDEA中配置环境变量"
