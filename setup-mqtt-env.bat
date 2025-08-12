@echo off
setlocal enabledelayedexpansion

echo === MQTT环境变量设置脚本 ===
echo.

REM 检查是否在项目根目录
if not exist "pom.xml" (
    echo ❌ 请在项目根目录运行此脚本
    pause
    exit /b 1
)

echo ✅ 检测到Maven项目
echo.

echo 请输入以下MQTT配置信息：
echo.

set /p instance_id="MQTT Instance ID (默认: your_instance_id_here): "
if "!instance_id!"=="" set instance_id=your_instance_id_here

set /p endpoint="MQTT Endpoint (默认: your_instance_id_here.mqtt.aliyuncs.com): "
if "!endpoint!"=="" set endpoint=your_instance_id_here.mqtt.aliyuncs.com

set /p access_key="MQTT Access Key: "
if "!access_key!"=="" (
    echo ❌ Access Key不能为空
    pause
    exit /b 1
)

set /p secret_key="MQTT Secret Key: "
if "!secret_key!"=="" (
    echo ❌ Secret Key不能为空
    pause
    exit /b 1
)

set /p group_id="MQTT Group ID (默认: GID_QUITTR): "
if "!group_id!"=="" set group_id=GID_QUITTR

set /p device_id="MQTT Device ID (默认: device-001): "
if "!device_id!"=="" set device_id=device-001

set /p parent_topic="MQTT Parent Topic (默认: CHAT_OFFICIAL_GROUP): "
if "!parent_topic!"=="" set parent_topic=CHAT_OFFICIAL_GROUP

echo.
echo === 配置信息确认 ===
echo Instance ID: !instance_id!
echo Endpoint: !endpoint!
echo Access Key: !access_key:~0,8!...
echo Secret Key: !secret_key:~0,8!...
echo Group ID: !group_id!
echo Device ID: !device_id!
echo Parent Topic: !parent_topic!
echo.

set /p confirm="确认以上配置信息正确吗？(y/N): "
if /i not "!confirm!"=="y" (
    echo ❌ 配置已取消
    pause
    exit /b 1
)

echo.
echo === 设置环境变量 ===

REM 设置环境变量
set MQTT_INSTANCE_ID=!instance_id!
set MQTT_ENDPOINT=!endpoint!
set MQTT_ACCESS_KEY=!access_key!
set MQTT_SECRET_KEY=!secret_key!
set MQTT_GROUP_ID=!group_id!
set MQTT_DEVICE_ID=!device_id!
set MQTT_PARENT_TOPIC=!parent_topic!
set MQTT_QOS_LEVEL=0
set MQTT_TCP_PORT=1883

echo ✅ 环境变量设置完成
echo.

REM 验证环境变量
echo === 环境变量验证 ===
echo MQTT_INSTANCE_ID: !MQTT_INSTANCE_ID!
echo MQTT_ENDPOINT: !MQTT_ENDPOINT!
echo MQTT_ACCESS_KEY: !MQTT_ACCESS_KEY:~0,8!...
echo MQTT_SECRET_KEY: !MQTT_SECRET_KEY:~0,8!...
echo MQTT_GROUP_ID: !MQTT_GROUP_ID!
echo MQTT_DEVICE_ID: !MQTT_DEVICE_ID!
echo MQTT_PARENT_TOPIC: !MQTT_PARENT_TOPIC!
echo.

echo === 创建本地配置文件 ===
(
echo # 本地MQTT配置 - 请勿提交到Git
echo mqtt:
echo   instance:
echo     id: !instance_id!
echo   endpoint: !endpoint!
echo   access-key: !access_key!
echo   secret-key: !secret_key!
echo   group:
echo     id: !group_id!
echo   device:
echo     id: !device_id!
echo   parent:
echo     topic: !parent_topic!
echo   qos:
echo     level: 0
echo   port:
echo     tcp: 1883
) > application-local.yml

echo ✅ 本地配置文件 application-local.yml 已创建
echo.

echo === 下一步操作 ===
echo 1. 在IDEA中设置环境变量，或者
echo 2. 使用命令: setup-mqtt-env.bat
echo 3. 启动应用测试MQTT连接
echo.

echo === 设置完成 ===
pause
