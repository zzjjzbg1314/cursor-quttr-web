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
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>Support - Nofapr</title>\n");
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
        html.append("            <h1>Support</h1>\n");
        html.append("            <p>We're here to help you anytime</p>\n");
        html.append("        </div>\n");
        
        // 欢迎与承诺部分
        html.append("        <div class=\"section\">\n");
        html.append("            <h2>").append(escapeHtml(welcome.get("title"))).append("</h2>\n");
        html.append("            <p style=\"font-size: 1.1em; margin-bottom: 15px;\">").append(escapeHtml(welcome.get("greeting"))).append("</p>\n");
        html.append("            <p>").append(escapeHtml(welcome.get("mission"))).append("</p>\n");
        html.append("        </div>\n");
        
        // FAQ section
        html.append("        <div class=\"section\">\n");
        html.append("            <h2>Frequently Asked Questions</h2>\n");
        
        // About app features
        List<Map<String, String>> functionFAQ = faq.get("function");
        if (functionFAQ != null && !functionFAQ.isEmpty()) {
            html.append("            <h3>About App Features</h3>\n");
            for (Map<String, String> item : functionFAQ) {
                html.append("            <div class=\"faq-item\">\n");
                html.append("                <strong>").append(escapeHtml(item.get("question"))).append("</strong>\n");
                html.append("                <p>").append(escapeHtml(item.get("answer"))).append("</p>\n");
                html.append("            </div>\n");
            }
        }
        
        // About account and privacy
        List<Map<String, String>> accountFAQ = faq.get("account");
        if (accountFAQ != null && !accountFAQ.isEmpty()) {
            html.append("            <h3>About Account & Privacy</h3>\n");
            for (Map<String, String> item : accountFAQ) {
                html.append("            <div class=\"faq-item\">\n");
                html.append("                <strong>").append(escapeHtml(item.get("question"))).append("</strong>\n");
                html.append("                <p>").append(escapeHtml(item.get("answer"))).append("</p>\n");
                html.append("            </div>\n");
            }
        }
        
        // About recovery journey
        List<Map<String, String>> journeyFAQ = faq.get("journey");
        if (journeyFAQ != null && !journeyFAQ.isEmpty()) {
            html.append("            <h3>About Recovery Journey</h3>\n");
            for (Map<String, String> item : journeyFAQ) {
                html.append("            <div class=\"faq-item\">\n");
                html.append("                <strong>").append(escapeHtml(item.get("question"))).append("</strong>\n");
                html.append("                <p>").append(escapeHtml(item.get("answer"))).append("</p>\n");
                html.append("            </div>\n");
            }
        }
        
        html.append("        </div>\n");
        
        // Contact us section
        html.append("        <div class=\"section\">\n");
        html.append("            <h2>").append(escapeHtml((String) contact.get("title"))).append("</h2>\n");
        html.append("            <p>").append(escapeHtml((String) contact.get("description"))).append("</p>\n");
        html.append("            <div class=\"contact-info\">\n");
        html.append("                <p><strong>Email:</strong> <a href=\"mailto:").append(escapeHtml((String) contact.get("email"))).append("\">").append(escapeHtml((String) contact.get("email"))).append("</a></p>\n");
        html.append("                <p><strong>").append(escapeHtml((String) contact.get("teamName"))).append("</strong></p>\n");
        html.append("                <p>").append(escapeHtml((String) contact.get("responseTime"))).append("</p>\n");
        @SuppressWarnings("unchecked")
        List<String> emailTips = (List<String>) contact.get("emailTips");
        if (emailTips != null && !emailTips.isEmpty()) {
            html.append("                <div class=\"email-tips\">\n");
            html.append("                    <p><strong>When sending an email, please include the following information:</strong></p>\n");
            html.append("                    <ul>\n");
            for (String tip : emailTips) {
                html.append("                        <li>").append(escapeHtml(tip)).append("</li>\n");
            }
            html.append("                    </ul>\n");
            html.append("                </div>\n");
        }
        html.append("            </div>\n");
        html.append("        </div>\n");
        
        // More resources section
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
                    html.append("                    <a href=\"").append(escapeHtml(url)).append("\" target=\"_blank\">Learn more →</a>\n");
                }
                html.append("                </div>\n");
            }
            html.append("            </div>\n");
        }
        html.append("        </div>\n");
        
        html.append("        <div class=\"footer\">\n");
        html.append("            <p>© 2024 Nofapr. All rights reserved.</p>\n");
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
        
        welcome.put("title", "Welcome & Commitment");
        welcome.put("greeting", "Thank you for choosing Nofapr and embarking on this journey of self-transformation. We commit to providing you with full support and companionship throughout this process.");
        welcome.put("mission", "Nofapr is dedicated to helping every user build healthy lifestyle habits and regain inner peace and freedom. " +
                "We believe that everyone has the power to change, and we will always be with you on this path, providing scientific methods and warm support. " +
                "You are not alone in this fight. Together with millions of users, we grow together.");
        
        return welcome;
    }
    
    /**
     * 构建常见问题解答
     */
    private Map<String, List<Map<String, String>>> buildFAQ() {
        Map<String, List<Map<String, String>>> faq = new HashMap<>();
        
        // About app features
        List<Map<String, String>> functionFAQ = new ArrayList<>();
        
        Map<String, String> q1 = new HashMap<>();
        q1.put("question", "How do I backup my data? Will I lose data after changing phones?");
        q1.put("answer", "All your data is automatically synced to cloud servers. As long as you log in with the same account, " +
                "you can see your complete recovery journey and all records on any device. We recommend regularly checking your account login status " +
                "to ensure data is syncing properly. After changing phones, simply re-download the app and log in with your account.");
        functionFAQ.add(q1);
        
        Map<String, String> q2 = new HashMap<>();
        q2.put("question", "How does the \"Emergency Support\" feature work?");
        q2.put("answer", "When you feel unable to resist temptation, you can tap the \"Emergency Support\" button. The app will immediately provide you with: " +
                "1) Emergency meditation guidance to help calm your mind; 2) Motivational quotes and success stories to rekindle your determination; " +
                "3) Quick distraction activity suggestions; 4) Community support where you can view encouragement from other members. " +
                "This feature is available anytime and is your strongest support.");
        functionFAQ.add(q2);
        
        Map<String, String> q3 = new HashMap<>();
        q3.put("question", "How do I set daily reminders?");
        q3.put("answer", "Go to the \"Settings\" page, select \"Reminders & Notifications\", and you can customize: " +
                "1) Daily check-in reminder time; 2) Motivational quote push frequency; 3) Recovery milestone celebration notifications. " +
                "We recommend setting reminders for times when you typically need the most support, such as evening alone time. " +
                "You can also turn off or adjust these reminders at any time.");
        functionFAQ.add(q3);
        
        faq.put("function", functionFAQ);
        
        // About account and privacy
        List<Map<String, String>> accountFAQ = new ArrayList<>();
        
        Map<String, String> q4 = new HashMap<>();
        q4.put("question", "Is my data safe? How do you protect my privacy?");
        q4.put("answer", "We understand that the content you record is extremely private and sensitive. We promise: " +
                "1) All data is encrypted during transmission and storage; " +
                "2) We will not share your personal data with any third parties; " +
                "3) You can choose to participate in community interactions using anonymous mode; " +
                "4) We follow the strictest data protection regulations. " +
                "For more details, please see our Privacy Policy. Your trust is what we value most.");
        accountFAQ.add(q4);
        
        Map<String, String> q5 = new HashMap<>();
        q5.put("question", "I forgot my password. How do I recover it?");
        q5.put("answer", "Click \"Forgot Password\" on the login page and follow these steps: " +
                "1) Enter the email or phone number you used to register; " +
                "2) We will send a verification code to your email/phone; " +
                "3) After entering the verification code, you can set a new password. " +
                "If you cannot receive the verification code, please contact us at keji_support@163.com, " +
                "and our team will manually assist you in recovering your account.");
        accountFAQ.add(q5);
        
        Map<String, String> q6 = new HashMap<>();
        q6.put("question", "I want to delete my account. How do I do that?");
        q6.put("answer", "We understand that everyone's situation is different. If you decide to delete your account: " +
                "1) Go to \"Settings\" -> \"Account Management\" -> \"Delete Account\"; " +
                "2) The system will confirm your decision again; " +
                "3) After confirmation, all your data will be permanently deleted from our servers within 7 days. " +
                "Please note: This operation is irreversible, and no data can be recovered after deletion. " +
                "If you need help, you can also email keji_support@163.com to request deletion.");
        accountFAQ.add(q6);
        
        faq.put("account", accountFAQ);
        
        // About recovery journey
        List<Map<String, String>> journeyFAQ = new ArrayList<>();
        
        Map<String, String> q7 = new HashMap<>();
        q7.put("question", "I feel like I can't keep going. What should I do?");
        q7.put("answer", "First, please don't blame yourself. Change is a process, and setbacks are part of growth. We recommend: " +
                "1) Use the \"Emergency Support\" feature to get immediate support; " +
                "2) Review your original intention and remember why you started this journey; " +
                "3) Lower expectations and start with small goals, like committing to one day; " +
                "4) Find companions in the community, share your feelings, and you'll discover many people have similar experiences; " +
                "5) If you still find it difficult after multiple attempts, consider seeking help from a professional counselor. " +
                "Remember: Seeking help is a sign of courage, not weakness.");
        journeyFAQ.add(q7);
        
        Map<String, String> q8 = new HashMap<>();
        q8.put("question", "Are the methods in the app scientific? What principles are they based on?");
        q8.put("answer", "Our methods are built on solid scientific foundations: " +
                "1) Neuroplasticity principle: The brain can rebuild neural pathways through consistent new habits; " +
                "2) Cognitive Behavioral Therapy (CBT): Helps identify and change negative thought patterns; " +
                "3) Mindfulness meditation: Enhances self-awareness and emotional regulation; " +
                "4) Habit formation theory: Builds positive habits through trigger-action-reward cycles; " +
                "5) Social support theory: The power of community significantly increases success rates. " +
                "Our content is designed with input from professionals in psychology and behavioral science, " +
                "and is continuously optimized based on user feedback and the latest research.");
        journeyFAQ.add(q8);
        
        faq.put("journey", journeyFAQ);
        
        return faq;
    }
    
    /**
     * 构建联系我们部分
     */
    private Map<String, Object> buildContact() {
        Map<String, Object> contact = new HashMap<>();
        
        contact.put("title", "Contact Us");
        contact.put("email", "keji_support@163.com");
        contact.put("description", "If you encounter any technical issues or have any suggestions, please feel free to contact us anytime.");
        
        List<String> emailTips = new ArrayList<>();
        emailTips.add("Your device model and operating system version");
        emailTips.add("A detailed description of the issue, and screenshots if possible");
        emailTips.add("Your account information (excluding password) to help us quickly identify the issue");
        contact.put("emailTips", emailTips);
        
        contact.put("responseTime", "We will reply to your email within 24 hours");
        contact.put("teamName", "Nofapr Development Team");
        
        return contact;
    }
    
    /**
     * 构建更多资源部分
     */
    private Map<String, Object> buildResources() {
        Map<String, Object> resources = new HashMap<>();
        
        resources.put("title", "More Resources");
        
        List<Map<String, String>> resourceList = new ArrayList<>();
        
        Map<String, String> resource2 = new HashMap<>();
        resource2.put("name", "App Community");
        resource2.put("description", "Join our community to exchange experiences and support each other with like-minded members");
        resource2.put("url", "");  // Can be filled in if there's a community link
        resourceList.add(resource2);
        
        Map<String, String> resource3 = new HashMap<>();
        resource3.put("name", "Science & Methods");
        resource3.put("description", "Learn more about the neuroscience principles behind recovery and effective self-discipline methods");
        resource3.put("url", "");  // Can be filled in if there's a blog or article link
        resourceList.add(resource3);
        
        Map<String, String> resource4 = new HashMap<>();
        resource4.put("name", "Professional Help");
        resource4.put("description", "If you need deeper psychological support, we recommend seeking help from a professional counselor");
        resource4.put("url", "");
        resourceList.add(resource4);
        
        resources.put("items", resourceList);
        
        return resources;
    }
}

