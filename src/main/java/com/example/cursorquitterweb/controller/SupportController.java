package com.example.cursorquitterweb.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 技术支持控制器
 */
@RestController
public class SupportController {

    /**
     * 获取技术支持页面
     */
    @GetMapping(value = "/support", produces = MediaType.TEXT_HTML_VALUE)
    public String getSupport() {
        Map<String, Object> supportData = new HashMap<>();
        
        // 欢迎与承诺
        supportData.put("welcome", buildWelcome());
        
        // 常见问题解答
        supportData.put("faq", buildFAQ());
        
        // 联系我们
        supportData.put("contact", buildContact());
        
        // 更多资源
        supportData.put("resources", buildResources());
        
        return buildHtmlPage(supportData);
    }
    
    /**
     * 构建 HTML 页面
     */
    private String buildHtmlPage(Map<String, Object> supportData) {
        @SuppressWarnings("unchecked")
        Map<String, String> welcome = (Map<String, String>) supportData.get("welcome");
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, String>>> faq = (Map<String, List<Map<String, String>>>) supportData.get("faq");
        @SuppressWarnings("unchecked")
        Map<String, Object> contact = (Map<String, Object>) supportData.get("contact");
        @SuppressWarnings("unchecked")
        Map<String, Object> resources = (Map<String, Object>) supportData.get("resources");
        
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"zh-CN\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>技术支持 - 克己</title>\n");
        html.append("    <style>\n");
        html.append("        * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        html.append("        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif; line-height: 1.6; color: #333; background: #f5f5f5; }\n");
        html.append("        .container { max-width: 1200px; margin: 0 auto; padding: 20px; }\n");
        html.append("        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 40px 20px; text-align: center; border-radius: 10px; margin-bottom: 30px; }\n");
        html.append("        .header h1 { font-size: 2.5em; margin-bottom: 10px; }\n");
        html.append("        .section { background: white; padding: 30px; margin-bottom: 30px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }\n");
        html.append("        .section h2 { color: #667eea; margin-bottom: 20px; padding-bottom: 10px; border-bottom: 2px solid #667eea; }\n");
        html.append("        .section h3 { color: #764ba2; margin-top: 25px; margin-bottom: 15px; }\n");
        html.append("        .faq-item { margin-bottom: 25px; padding: 20px; background: #f9f9f9; border-left: 4px solid #667eea; border-radius: 5px; }\n");
        html.append("        .faq-item strong { color: #667eea; display: block; margin-bottom: 10px; font-size: 1.1em; }\n");
        html.append("        .faq-item p { color: #666; line-height: 1.8; }\n");
        html.append("        .contact-info { background: #f0f7ff; padding: 20px; border-radius: 5px; margin-top: 20px; }\n");
        html.append("        .contact-info a { color: #667eea; text-decoration: none; font-weight: bold; }\n");
        html.append("        .contact-info a:hover { text-decoration: underline; }\n");
        html.append("        .email-tips { margin-top: 15px; padding-left: 20px; }\n");
        html.append("        .email-tips li { margin-bottom: 8px; color: #666; }\n");
        html.append("        .resources { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; margin-top: 20px; }\n");
        html.append("        .resource-item { padding: 20px; background: #f9f9f9; border-radius: 5px; border: 1px solid #e0e0e0; }\n");
        html.append("        .resource-item h4 { color: #667eea; margin-bottom: 10px; }\n");
        html.append("        .resource-item p { color: #666; font-size: 0.9em; margin-bottom: 10px; }\n");
        html.append("        .resource-item a { color: #764ba2; text-decoration: none; font-weight: bold; }\n");
        html.append("        .resource-item a:hover { text-decoration: underline; }\n");
        html.append("        .footer { text-align: center; padding: 20px; color: #999; margin-top: 40px; }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"container\">\n");
        html.append("        <div class=\"header\">\n");
        html.append("            <h1>技术支持</h1>\n");
        html.append("            <p>我们随时为您提供帮助</p>\n");
        html.append("        </div>\n");
        
        // 欢迎与承诺部分
        html.append("        <div class=\"section\">\n");
        html.append("            <h2>").append(escapeHtml(welcome.get("title"))).append("</h2>\n");
        html.append("            <p style=\"font-size: 1.1em; margin-bottom: 15px;\">").append(escapeHtml(welcome.get("greeting"))).append("</p>\n");
        html.append("            <p>").append(escapeHtml(welcome.get("mission"))).append("</p>\n");
        html.append("        </div>\n");
        
        // 常见问题解答部分
        html.append("        <div class=\"section\">\n");
        html.append("            <h2>常见问题解答</h2>\n");
        
        // 关于应用功能
        List<Map<String, String>> functionFAQ = faq.get("function");
        if (functionFAQ != null && !functionFAQ.isEmpty()) {
            html.append("            <h3>关于应用功能</h3>\n");
            for (Map<String, String> item : functionFAQ) {
                html.append("            <div class=\"faq-item\">\n");
                html.append("                <strong>").append(escapeHtml(item.get("question"))).append("</strong>\n");
                html.append("                <p>").append(escapeHtml(item.get("answer"))).append("</p>\n");
                html.append("            </div>\n");
            }
        }
        
        // 关于账户与隐私
        List<Map<String, String>> accountFAQ = faq.get("account");
        if (accountFAQ != null && !accountFAQ.isEmpty()) {
            html.append("            <h3>关于账户与隐私</h3>\n");
            for (Map<String, String> item : accountFAQ) {
                html.append("            <div class=\"faq-item\">\n");
                html.append("                <strong>").append(escapeHtml(item.get("question"))).append("</strong>\n");
                html.append("                <p>").append(escapeHtml(item.get("answer"))).append("</p>\n");
                html.append("            </div>\n");
            }
        }
        
        // 关于戒色之旅
        List<Map<String, String>> journeyFAQ = faq.get("journey");
        if (journeyFAQ != null && !journeyFAQ.isEmpty()) {
            html.append("            <h3>关于戒色之旅</h3>\n");
            for (Map<String, String> item : journeyFAQ) {
                html.append("            <div class=\"faq-item\">\n");
                html.append("                <strong>").append(escapeHtml(item.get("question"))).append("</strong>\n");
                html.append("                <p>").append(escapeHtml(item.get("answer"))).append("</p>\n");
                html.append("            </div>\n");
            }
        }
        
        html.append("        </div>\n");
        
        // 联系我们部分
        html.append("        <div class=\"section\">\n");
        html.append("            <h2>").append(escapeHtml((String) contact.get("title"))).append("</h2>\n");
        html.append("            <p>").append(escapeHtml((String) contact.get("description"))).append("</p>\n");
        html.append("            <div class=\"contact-info\">\n");
        html.append("                <p><strong>邮箱：</strong><a href=\"mailto:").append(escapeHtml((String) contact.get("email"))).append("\">").append(escapeHtml((String) contact.get("email"))).append("</a></p>\n");
        html.append("                <p><strong>").append(escapeHtml((String) contact.get("teamName"))).append("</strong></p>\n");
        html.append("                <p>").append(escapeHtml((String) contact.get("responseTime"))).append("</p>\n");
        @SuppressWarnings("unchecked")
        List<String> emailTips = (List<String>) contact.get("emailTips");
        if (emailTips != null && !emailTips.isEmpty()) {
            html.append("                <div class=\"email-tips\">\n");
            html.append("                    <p><strong>发送邮件时，请包含以下信息：</strong></p>\n");
            html.append("                    <ul>\n");
            for (String tip : emailTips) {
                html.append("                        <li>").append(escapeHtml(tip)).append("</li>\n");
            }
            html.append("                    </ul>\n");
            html.append("                </div>\n");
        }
        html.append("            </div>\n");
        html.append("        </div>\n");
        
        // 更多资源部分
        html.append("        <div class=\"section\">\n");
        html.append("            <h2>").append(escapeHtml((String) resources.get("title"))).append("</h2>\n");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> resourceList = (List<Map<String, String>>) resources.get("items");
        if (resourceList != null && !resourceList.isEmpty()) {
            html.append("            <div class=\"resources\">\n");
            for (Map<String, String> resource : resourceList) {
                html.append("                <div class=\"resource-item\">\n");
                html.append("                    <h4>").append(escapeHtml(resource.get("name"))).append("</h4>\n");
                html.append("                    <p>").append(escapeHtml(resource.get("description"))).append("</p>\n");
                String url = resource.get("url");
                if (url != null && !url.isEmpty()) {
                    html.append("                    <a href=\"").append(escapeHtml(url)).append("\" target=\"_blank\">了解更多 →</a>\n");
                }
                html.append("                </div>\n");
            }
            html.append("            </div>\n");
        }
        html.append("        </div>\n");
        
        html.append("        <div class=\"footer\">\n");
        html.append("            <p>© 2024 克己. 保留所有权利.</p>\n");
        html.append("        </div>\n");
        html.append("    </div>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        return html.toString();
    }
    
    /**
     * HTML 转义
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
    }
    
    /**
     * 构建欢迎与承诺部分
     */
    private Map<String, String> buildWelcome() {
        Map<String, String> welcome = new HashMap<>();
        
        welcome.put("title", "欢迎与承诺");
        welcome.put("greeting", "感谢您选择克己，踏上这段重塑自我的旅程。我们在此承诺，为您提供全程的支持与陪伴。");
        welcome.put("mission", "克己致力于帮助每一位用户建立健康的生活习惯，重获内心的平静与自由。" +
                "我们相信，每个人都有改变的力量，而我们会在这条路上始终陪伴您，提供科学的方法和温暖的支持。" +
                "您不是一个人在战斗，我们与千万用户一起，共同成长。");
        
        return welcome;
    }
    
    /**
     * 构建常见问题解答
     */
    private Map<String, List<Map<String, String>>> buildFAQ() {
        Map<String, List<Map<String, String>>> faq = new HashMap<>();
        
        // 关于应用功能
        List<Map<String, String>> functionFAQ = new ArrayList<>();
        
        Map<String, String> q1 = new HashMap<>();
        q1.put("question", "如何备份我的数据？换手机后数据会丢失吗？");
        q1.put("answer", "您的所有数据都会自动同步到云端服务器。只要您使用相同的账号登录，" +
                "无论在哪台设备上，都能看到您的完整戒断历程和所有记录。我们建议您定期检查账号登录状态，" +
                "确保数据正常同步。换手机后，只需重新下载应用并登录您的账号即可。");
        functionFAQ.add(q1);
        
        Map<String, String> q2 = new HashMap<>();
        q2.put("question", "\"紧急援助\"功能是如何工作的？");
        q2.put("answer", "当您感到难以抵抗诱惑时，可以点击\"紧急援助\"按钮。应用会立即为您提供：" +
                "1) 紧急冥想引导，帮助您平复心情；2) 激励语录和成功案例，重燃您的决心；" +
                "3) 快速转移注意力的建议活动；4) 社区支持，您可以查看其他伙伴的鼓励。" +
                "这个功能随时可用，是您最坚实的后盾。");
        functionFAQ.add(q2);
        
        Map<String, String> q3 = new HashMap<>();
        q3.put("question", "如何设置每日提醒？");
        q3.put("answer", "进入\"设置\"页面，选择\"提醒与通知\"，您可以自定义：" +
                "1) 每日打卡提醒时间；2) 激励语录推送频率；3) 戒断里程碑庆祝通知。" +
                "我们建议在您通常最需要支持的时刻设置提醒，比如晚间独处时段。" +
                "您也可以随时关闭或调整这些提醒。");
        functionFAQ.add(q3);
        
        faq.put("function", functionFAQ);
        
        // 关于账户与隐私
        List<Map<String, String>> accountFAQ = new ArrayList<>();
        
        Map<String, String> q4 = new HashMap<>();
        q4.put("question", "我的数据安全吗？你们如何保护我的隐私？");
        q4.put("answer", "我们深知您记录的内容极其私密和敏感。我们承诺：" +
                "1) 所有数据在传输和存储时都经过加密；" +
                "2) 我们不会与任何第三方分享您的个人数据；" +
                "3) 您可以选择使用匿名模式参与社区互动；" +
                "4) 我们遵循最严格的数据保护法规。" +
                "详细信息请查看我们的隐私政策。您的信任是我们最珍视的。");
        accountFAQ.add(q4);
        
        Map<String, String> q5 = new HashMap<>();
        q5.put("question", "我忘记了密码，该如何找回？");
        q5.put("answer", "在登录页面点击\"忘记密码\"，按照以下步骤操作：" +
                "1) 输入您注册时使用的邮箱或手机号；" +
                "2) 我们会发送验证码到您的邮箱/手机；" +
                "3) 输入验证码后，您可以设置新密码。" +
                "如果您无法接收验证码，请通过 keji_support@163.com 联系我们，" +
                "我们的团队会手动协助您找回账号。");
        accountFAQ.add(q5);
        
        Map<String, String> q6 = new HashMap<>();
        q6.put("question", "我想删除我的账户，该如何操作？");
        q6.put("answer", "我们理解每个人的情况不同。如果您决定删除账户：" +
                "1) 进入\"设置\" -> \"账户管理\" -> \"删除账户\"；" +
                "2) 系统会再次确认您的决定；" +
                "3) 确认后，您的所有数据将在7天内从我们的服务器永久删除。" +
                "请注意：此操作不可逆，删除后无法恢复任何数据。" +
                "如需帮助，也可以发邮件至 keji_support@163.com 申请删除。");
        accountFAQ.add(q6);
        
        faq.put("account", accountFAQ);
        
        // 关于戒色之旅
        List<Map<String, String>> journeyFAQ = new ArrayList<>();
        
        Map<String, String> q7 = new HashMap<>();
        q7.put("question", "我感觉坚持不下去了，该怎么办？");
        q7.put("answer", "首先，请不要责备自己。改变是一个过程，挫折是成长的一部分。我们建议：" +
                "1) 使用\"紧急援助\"功能，立即获取支持；" +
                "2) 回顾您的初心，重温您当初为什么开始这段旅程；" +
                "3) 降低期望，从小目标开始，比如先坚持一天；" +
                "4) 在社区寻找同伴，分享您的感受，您会发现很多人有相似的经历；" +
                "5) 如果多次尝试仍感困难，建议寻求专业心理咨询师的帮助。" +
                "记住：寻求帮助是勇敢的表现，不是软弱。");
        journeyFAQ.add(q7);
        
        Map<String, String> q8 = new HashMap<>();
        q8.put("question", "应用内的方法科学吗？是基于什么原理？");
        q8.put("answer", "我们的方法建立在扎实的科学基础之上：" +
                "1) 神经可塑性原理：大脑可以通过持续的新习惯重新建立神经通路；" +
                "2) 认知行为疗法(CBT)：帮助识别和改变不良思维模式；" +
                "3) 正念冥想：增强自我觉察和情绪调节能力；" +
                "4) 习惯形成理论：通过触发-行动-奖励循环建立正向习惯；" +
                "5) 社会支持理论：社群的力量能显著提高成功率。" +
                "我们的内容由心理学和行为科学领域的专业人士参与设计，" +
                "并持续根据用户反馈和最新研究进行优化。");
        journeyFAQ.add(q8);
        
        faq.put("journey", journeyFAQ);
        
        return faq;
    }
    
    /**
     * 构建联系我们部分
     */
    private Map<String, Object> buildContact() {
        Map<String, Object> contact = new HashMap<>();
        
        contact.put("title", "联系我们");
        contact.put("email", "keji_support@163.com");
        contact.put("description", "如果您遇到任何技术问题或有任何建议，欢迎随时联系我们。");
        
        List<String> emailTips = new ArrayList<>();
        emailTips.add("您使用的设备型号和操作系统版本");
        emailTips.add("问题的详细描述，如果可以，请附上截图");
        emailTips.add("您的账号信息（不包括密码）以便我们快速定位问题");
        contact.put("emailTips", emailTips);
        
        contact.put("responseTime", "我们会在24小时内回复您的邮件");
        contact.put("teamName", "克己开发团队");
        
        return contact;
    }
    
    /**
     * 构建更多资源部分
     */
    private Map<String, Object> buildResources() {
        Map<String, Object> resources = new HashMap<>();
        
        resources.put("title", "更多资源");
        
        List<Map<String, String>> resourceList = new ArrayList<>();
        
        Map<String, String> resource2 = new HashMap<>();
        resource2.put("name", "应用社区");
        resource2.put("description", "加入我们的社区，与志同道合的伙伴交流经验、互相支持");
        resource2.put("url", "");  // 如果有社区链接可以填入
        resourceList.add(resource2);
        
        Map<String, String> resource3 = new HashMap<>();
        resource3.put("name", "科学与方法");
        resource3.put("description", "深入了解戒断背后的神经科学原理和有效的自律方法");
        resource3.put("url", "");  // 如果有博客或文章链接可以填入
        resourceList.add(resource3);
        
        Map<String, String> resource4 = new HashMap<>();
        resource4.put("name", "专业帮助");
        resource4.put("description", "如果您需要更深度的心理支持，我们建议寻求专业心理咨询师的帮助");
        resource4.put("url", "");
        resourceList.add(resource4);
        
        resources.put("items", resourceList);
        
        return resources;
    }
}

