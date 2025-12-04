package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 隐私政策控制器
 */
@RestController
public class PrivacyController {

    /**
     * 获取隐私政策
     */
    @GetMapping("/privacy")
    public ApiResponse<Map<String, Object>> getPrivacyPolicy() {
        Map<String, Object> privacyData = new HashMap<>();
        
        privacyData.put("title", "Nofapr Privacy Policy");
        privacyData.put("lastUpdated", "2025.10.25");
        privacyData.put("content", buildPrivacyContent());
        
        return ApiResponse.success(privacyData);
    }
    
    /**
     * 构建隐私政策内容
     */
    private String buildPrivacyContent() {
        StringBuilder content = new StringBuilder();
        
        content.append("Nofapr Privacy Policy\n");
        content.append("Last Updated: October 25, 2025\n\n");
        
        content.append("Nofapr (hereinafter referred to as \"we\" or \"the app\") understands the importance of privacy information to you and is committed to protecting your privacy. ");
        content.append("This policy aims to explain how we collect, use, store, and share your personal information, as well as how you can exercise your privacy rights. ");
        content.append("Please read and understand this policy carefully before using our services.\n\n");
        
        content.append("1. Information We Collect\n");
        content.append("We follow the \"principle of minimization\" and only collect information necessary to maintain the core functions of the app.\n\n");
        
        content.append("a) Information You Directly Provide:\n\n");
        content.append("Account Information (if registered): When you create an account, we may collect hashed anonymous identifiers (strongly recommended), ");
        content.append("or only collect nicknames and passwords necessary to complete the service. We do not recommend collecting email or phone numbers to maximize your anonymity.\n\n");
        content.append("User-Generated Content: Content you actively record within the app, such as recovery journals, mood tracking, community posts (if available), etc. ");
        content.append("This data is only used to sync across your devices and provide you with personalized services.\n\n");
        
        content.append("b) Information We Automatically Collect:\n\n");
        content.append("Usage Data: We may collect anonymized information such as features you access, usage duration, click records, etc., ");
        content.append("for overall data analysis to improve the product, and cannot be linked to specific individuals.\n\n");
        content.append("Device Information: To ensure service security and compatibility, we may collect device type, operating system version, anonymous device identifiers, etc.\n\n");
        
        content.append("c) Sensitive Information:\n\n");
        content.append("We recognize that recovery data, mood logs, etc., that you record may constitute sensitive personal information. ");
        content.append("We promise never to use this data for any other purpose, and only use it to provide you with core recovery support services after obtaining your explicit consent.\n\n");
        
        content.append("2. How We Use Your Information\n");
        content.append("To provide and maintain services, ensuring the app operates normally.\n");
        content.append("To personalize your experience and present you with relevant motivational content and progress reports.\n");
        content.append("For anonymized analysis to understand overall user behavior, thereby improving and optimizing our features and services.\n");
        content.append("To communicate with you, such as sending notifications about service updates (you can opt out).\n\n");
        
        content.append("3. Information Sharing and Disclosure\n");
        content.append("We will never sell, trade, or rent your personally identifiable information to any third party. We may only share information in the following extremely limited circumstances:\n\n");
        content.append("With your explicit consent.\n");
        content.append("Legal Compliance: If we believe disclosure is necessary to comply with laws, regulations, subpoenas, or court orders.\n");
        content.append("Service Providers: We may engage third-party service providers bound by contracts (such as data analytics platforms like Crashlytics, cloud service providers) to assist us. ");
        content.append("They can only access information necessary to complete their services and are prohibited from using the information for any other purpose.\n\n");
        
        content.append("4. Data Security\n");
        content.append("We have implemented various security measures, including data encryption (at rest and in transit), to protect your personal information from unauthorized access, alteration, disclosure, or destruction.\n\n");
        
        content.append("5. Data Retention\n");
        content.append("We will retain your information as long as your account exists. If you wish to delete your account, you can do so directly within the app, ");
        content.append("or contact us at keji_support@163.com. After account deletion, all your personal data will be permanently removed from our servers.\n\n");
        
        content.append("6. Your Rights\n");
        content.append("You have the right to:\n");
        content.append("Access personal information we hold about you.\n");
        content.append("Correct inaccurate personal information.\n");
        content.append("Delete your personal information.\n");
        content.append("Restrict or object to our processing of your personal information.\n");
        content.append("Revoke consent for our data collection at any time through device permission settings.\n\n");
        
        content.append("7. Children's Privacy\n");
        content.append("Our services are not directed at individuals under the age of 13. If we discover that we have collected information from children under 13, we will immediately delete it.\n\n");
        
        content.append("8. Changes to This Policy\n");
        content.append("We may update this policy from time to time. After updates, we will publish a new privacy policy within the app and update the \"Last Updated\" date at the top. ");
        content.append("Please review it regularly.\n\n");
        
        content.append("9. Contact Us\n");
        content.append("If you have any questions, comments, or suggestions regarding this privacy policy, please contact us through the following:\n");
        content.append("Nofapr Development Team\n");
        content.append("Email: keji_support@163.com\n");
        
        return content.toString();
    }
}

