#!/bin/bash

echo "=== MQTT环境变量设置脚本 ==="
echo ""

# 检查是否在项目根目录
if [ ! -f "pom.xml" ]; then
    echo "❌ 请在项目根目录运行此脚本"
    exit 1
fi

echo "✅ 检测到Maven项目"
echo ""

# 提示用户输入配置信息
echo "请输入以下MQTT配置信息："
echo ""

read -p "MQTT Instance ID (默认: your_instance_id_here): " instance_id
instance_id=${instance_id:-your_instance_id_here}

read -p "MQTT Endpoint (默认: your_instance_id_here.mqtt.aliyuncs.com): " endpoint
endpoint=${endpoint:-your_instance_id_here.mqtt.aliyuncs.com}

read -p "MQTT Access Key: " access_key
if [ -z "$access_key" ]; then
    echo "❌ Access Key不能为空"
    exit 1
fi

read -p "MQTT Secret Key: " secret_key
if [ -z "$secret_key" ]; then
    echo "❌ Secret Key不能为空"
    exit 1
fi

read -p "MQTT Group ID (默认: GID_QUITTR): " group_id
group_id=${group_id:-GID_QUITTR}

read -p "MQTT Device ID (默认: $(uuidgen)): " device_id
device_id=${device_id:-$(uuidgen)}

read -p "MQTT Parent Topic (默认: CHAT_OFFICIAL_GROUP): " parent_topic
parent_topic=${parent_topic:-CHAT_OFFICIAL_GROUP}

echo ""
echo "=== 配置信息确认 ==="
echo "Instance ID: $instance_id"
echo "Endpoint: $endpoint"
echo "Access Key: ${access_key:0:8}..."
echo "Secret Key: ${secret_key:0:8}..."
echo "Group ID: $group_id"
echo "Device ID: $device_id"
echo "Parent Topic: $parent_topic"
echo ""

read -p "确认以上配置信息正确吗？(y/N): " confirm
if [[ ! $confirm =~ ^[Yy]$ ]]; then
    echo "❌ 配置已取消"
    exit 1
fi

echo ""
echo "=== 设置环境变量 ==="

# 设置环境变量
export MQTT_INSTANCE_ID="$instance_id"
export MQTT_ENDPOINT="$endpoint"
export MQTT_ACCESS_KEY="$access_key"
export MQTT_SECRET_KEY="$secret_key"
export MQTT_GROUP_ID="$group_id"
export MQTT_DEVICE_ID="$device_id"
export MQTT_PARENT_TOPIC="$parent_topic"
export MQTT_QOS_LEVEL="0"
export MQTT_TCP_PORT="1883"

echo "✅ 环境变量设置完成"
echo ""

# 验证环境变量
echo "=== 环境变量验证 ==="
echo "MQTT_INSTANCE_ID: $MQTT_INSTANCE_ID"
echo "MQTT_ENDPOINT: $MQTT_ENDPOINT"
echo "MQTT_ACCESS_KEY: ${MQTT_ACCESS_KEY:0:8}..."
echo "MQTT_SECRET_KEY: ${MQTT_SECRET_KEY:0:8}..."
echo "MQTT_GROUP_ID: $MQTT_GROUP_ID"
echo "MQTT_DEVICE_ID: $MQTT_DEVICE_ID"
echo "MQTT_PARENT_TOPIC: $MQTT_PARENT_TOPIC"
echo ""

# 创建本地配置文件
echo "=== 创建本地配置文件 ==="
cat > application-local.yml << EOF
# 本地MQTT配置 - 请勿提交到Git
mqtt:
  instance:
    id: $instance_id
  endpoint: $endpoint
  access-key: $access_key
  secret-key: $secret_key
  group:
    id: $group_id
  device:
    id: $device_id
  parent:
    topic: $parent_topic
  qos:
    level: 0
  port:
    tcp: 1883
EOF

echo "✅ 本地配置文件 application-local.yml 已创建"
echo ""

echo "=== 下一步操作 ==="
echo "1. 在IDEA中设置环境变量，或者"
echo "2. 使用命令: source setup-mqtt-env.sh"
echo "3. 启动应用测试MQTT连接"
echo ""

echo "=== 设置完成 ==="
