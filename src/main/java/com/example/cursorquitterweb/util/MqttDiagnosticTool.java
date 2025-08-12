package com.example.cursorquitterweb.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.time.LocalDateTime;

/**
 * MQTT连接诊断工具类
 * 用于诊断MQTT连接问题和网络连通性
 */
@Component
public class MqttDiagnosticTool {
    
    @Value("${mqtt.broker.url:ssl://your_instance_id_here.mqtt.aliyuncs.com:8883}")
    private String brokerUrl;
    
    /**
     * 执行完整的MQTT连接诊断
     */
    public void performFullDiagnostic() {
        System.out.println("=== MQTT连接完整诊断开始 ===");
        System.out.println("诊断时间: " + LocalDateTime.now());
        System.out.println("目标MQTT服务器: " + brokerUrl);
        
        // 1. 解析MQTT服务器地址
        parseBrokerUrl();
        
        // 2. 检查网络连通性
        checkNetworkConnectivity();
        
        // 3. 检查端口连通性
        checkPortConnectivity();
        
        // 4. 检查DNS解析
        checkDnsResolution();
        
        // 5. 检查SSL/TLS支持
        checkSslSupport();
        
        System.out.println("=== MQTT连接完整诊断完成 ===");
    }
    
    /**
     * 解析MQTT服务器URL
     */
    private void parseBrokerUrl() {
        System.out.println("--- 解析MQTT服务器URL ---");
        try {
            if (brokerUrl.startsWith("ssl://")) {
                System.out.println("协议: SSL/TLS");
                String hostPort = brokerUrl.substring(6);
                parseHostPort(hostPort);
            } else if (brokerUrl.startsWith("tcp://")) {
                System.out.println("协议: TCP");
                String hostPort = brokerUrl.substring(6);
                parseHostPort(hostPort);
            } else if (brokerUrl.startsWith("ws://")) {
                System.out.println("协议: WebSocket");
                String hostPort = brokerUrl.substring(5);
                parseHostPort(hostPort);
            } else if (brokerUrl.startsWith("wss://")) {
                System.out.println("协议: WebSocket Secure");
                String hostPort = brokerUrl.substring(6);
                parseHostPort(hostPort);
            } else {
                System.out.println("协议: 未知");
                parseHostPort(brokerUrl);
            }
        } catch (Exception e) {
            System.out.println("URL解析失败: " + e.getMessage());
        }
    }
    
    /**
     * 解析主机和端口
     */
    private void parseHostPort(String hostPort) {
        String[] parts = hostPort.split(":");
        if (parts.length >= 2) {
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);
            System.out.println("主机: " + host);
            System.out.println("端口: " + port);
        } else {
            System.out.println("主机: " + hostPort);
            System.out.println("端口: 未指定");
        }
    }
    
    /**
     * 检查网络连通性
     */
    private void checkNetworkConnectivity() {
        System.out.println("--- 检查网络连通性 ---");
        
        // 检查基本网络连通性
        try {
            String host = extractHost();
            System.out.println("正在ping主机: " + host);
            
            ProcessBuilder pb = new ProcessBuilder("ping", "-c", "3", host);
            Process process = pb.start();
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("网络连通性: 正常");
            } else {
                System.out.println("网络连通性: 异常 (退出码: " + exitCode + ")");
            }
            
        } catch (Exception e) {
            System.out.println("网络连通性检查失败: " + e.getMessage());
            System.out.println("可能的原因: ping命令不可用或权限不足");
        }
    }
    
    /**
     * 检查端口连通性
     */
    private void checkPortConnectivity() {
        System.out.println("--- 检查端口连通性 ---");
        
        try {
            String host = extractHost();
            int port = extractPort();
            
            System.out.println("正在检查端口 " + port + " 的连通性...");
            
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 10000); // 10秒超时
                System.out.println("端口 " + port + " 连通性: 正常");
            } catch (IOException e) {
                System.out.println("端口 " + port + " 连通性: 失败");
                System.out.println("错误信息: " + e.getMessage());
                
                if (e.getMessage().contains("Connection refused")) {
                    System.out.println("可能原因: 服务未启动或端口未开放");
                } else if (e.getMessage().contains("Connection timed out")) {
                    System.out.println("可能原因: 网络延迟过高或防火墙阻止");
                } else if (e.getMessage().contains("No route to host")) {
                    System.out.println("可能原因: 网络路由问题");
                }
            }
            
        } catch (Exception e) {
            System.out.println("端口连通性检查失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查DNS解析
     */
    private void checkDnsResolution() {
        System.out.println("--- 检查DNS解析 ---");
        
        try {
            String host = extractHost();
            System.out.println("正在解析主机名: " + host);
            
            java.net.InetAddress[] addresses = java.net.InetAddress.getAllByName(host);
            System.out.println("DNS解析成功，找到 " + addresses.length + " 个IP地址:");
            
            for (java.net.InetAddress address : addresses) {
                System.out.println("  " + address.getHostAddress() + " (" + address.getHostName() + ")");
            }
            
        } catch (Exception e) {
            System.out.println("DNS解析失败: " + e.getMessage());
            System.out.println("可能原因: DNS服务器配置问题或主机名不存在");
        }
    }
    
    /**
     * 检查SSL/TLS支持
     */
    private void checkSslSupport() {
        System.out.println("--- 检查SSL/TLS支持 ---");
        
        if (brokerUrl.startsWith("ssl://") || brokerUrl.startsWith("wss://")) {
            System.out.println("检测到SSL/TLS连接，检查SSL支持...");
            
            try {
                // 检查可用的SSL协议
                String[] protocols = {"TLSv1.2", "TLSv1.3"};
                System.out.println("支持的SSL协议:");
                
                for (String protocol : protocols) {
                    try {
                        javax.net.ssl.SSLContext context = javax.net.ssl.SSLContext.getInstance(protocol);
                        System.out.println("  " + protocol + ": 支持");
                    } catch (Exception e) {
                        System.out.println("  " + protocol + ": 不支持");
                    }
                }
                
                // 检查可用的加密套件
                System.out.println("可用的加密套件数量: " + 
                    javax.net.ssl.SSLContext.getInstance("TLS").getDefaultSSLParameters().getCipherSuites().length);
                
            } catch (Exception e) {
                System.out.println("SSL支持检查失败: " + e.getMessage());
            }
        } else {
            System.out.println("非SSL连接，跳过SSL检查");
        }
    }
    
    /**
     * 提取主机名
     */
    private String extractHost() {
        String url = brokerUrl;
        if (url.startsWith("ssl://")) {
            url = url.substring(6);
        } else if (url.startsWith("tcp://")) {
            url = url.substring(6);
        } else if (url.startsWith("ws://")) {
            url = url.substring(5);
        } else if (url.startsWith("wss://")) {
            url = url.substring(6);
        }
        
        String[] parts = url.split(":");
        return parts[0];
    }
    
    /**
     * 提取端口号
     */
    private int extractPort() {
        String url = brokerUrl;
        if (url.startsWith("ssl://")) {
            url = url.substring(6);
        } else if (url.startsWith("tcp://")) {
            url = url.substring(6);
        } else if (url.startsWith("ws://")) {
            url = url.substring(5);
        } else if (url.startsWith("wss://")) {
            url = url.substring(6);
        }
        
        String[] parts = url.split(":");
        if (parts.length >= 2) {
            return Integer.parseInt(parts[1]);
        }
        
        // 返回默认端口
        if (brokerUrl.startsWith("ssl://")) {
            return 8883; // SSL默认端口
        } else if (brokerUrl.startsWith("tcp://")) {
            return 1883; // TCP默认端口
        } else if (brokerUrl.startsWith("ws://")) {
            return 80;   // WebSocket默认端口
        } else if (brokerUrl.startsWith("wss://")) {
            return 443;  // WebSocket Secure默认端口
        }
        
        return 1883; // 默认MQTT端口
    }
    
    /**
     * 获取诊断报告
     */
    public String getDiagnosticReport() {
        StringBuilder report = new StringBuilder();
        report.append("MQTT连接诊断报告\n");
        report.append("================\n");
        report.append("诊断时间: ").append(LocalDateTime.now()).append("\n");
        report.append("目标服务器: ").append(brokerUrl).append("\n");
        report.append("协议: ").append(brokerUrl.startsWith("ssl://") ? "SSL/TLS" : "TCP").append("\n");
        report.append("主机: ").append(extractHost()).append("\n");
        report.append("端口: ").append(extractPort()).append("\n");
        
        return report.toString();
    }
}
