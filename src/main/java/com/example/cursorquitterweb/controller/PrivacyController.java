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
        
        privacyData.put("title", "《克己 隐私政策》");
        privacyData.put("lastUpdated", "2025.10.25");
        privacyData.put("content", buildPrivacyContent());
        
        return ApiResponse.success(privacyData);
    }
    
    /**
     * 构建隐私政策内容
     */
    private String buildPrivacyContent() {
        StringBuilder content = new StringBuilder();
        
        content.append("《克己 隐私政策》\n");
        content.append("最后更新日期：2025.10.25\n\n");
        
        content.append("克己（以下简称\"我们\"或\"应用\"）深知隐私信息对您的重要性，并致力于保护您的隐私。");
        content.append("本政策旨在说明我们如何收集、使用、存储和共享您的个人信息，以及您如何行使您的隐私权利。");
        content.append("请在使用我们的服务前，仔细阅读并了解本政策。\n\n");
        
        content.append("1. 我们收集的信息\n");
        content.append("我们遵循\"最小化原则\"，仅收集维持应用核心功能所必需的信息。\n\n");
        
        content.append("a) 您直接提供的信息：\n\n");
        content.append("账户信息（如果注册）：当您创建账户时，我们可能会收集经过哈希处理的匿名标识符（强烈推荐），");
        content.append("或仅收集为完成服务所必需的昵称和密码。不建议收集邮箱或手机号，以最大限度保护您的匿名性。\n\n");
        content.append("用户生成内容：您在应用内主动记录的内容，如戒断日记、心情追踪、社区发帖（如果具备）等。");
        content.append("这些数据仅用于在您的设备间同步和为您提供个性化服务。\n\n");
        
        content.append("b) 我们自动收集的信息：\n\n");
        content.append("使用数据：我们可能会收集匿名化的信息，如您访问的功能、使用时长、点击记录等，");
        content.append("用于整体性的数据分析以改进产品，无法关联到具体个人。\n\n");
        content.append("设备信息：为保障服务安全与兼容性，我们可能会收集设备类型、操作系统版本、匿名设备标识符等。\n\n");
        
        content.append("c) 敏感信息：\n\n");
        content.append("我们意识到您记录的戒断数据、心情日志等可能属于敏感个人信息。");
        content.append("我们承诺绝不将此数据用于任何其他目的，仅在获得您明确同意后，用于为您提供核心的戒色支持服务。\n\n");
        
        content.append("2. 我们如何使用您的信息\n");
        content.append("提供和维护服务，确保应用正常运行。\n");
        content.append("个性化您的体验，为您呈现相关的激励内容和进度报告。\n");
        content.append("匿名化分析，以理解整体用户行为，从而改进和优化我们的功能与服务。\n");
        content.append("与您沟通，例如发送关于服务更新的通知（您可以选择退订）。\n\n");
        
        content.append("3. 信息的共享与披露\n");
        content.append("我们绝不会出售、交易或出租您的个人可识别信息给任何第三方。仅在以下极有限的情况下，我们可能会共享信息：\n\n");
        content.append("征得您的明确同意。\n");
        content.append("遵守法律：如果我们确信为了遵守法律、法规、传票或法院命令，披露是必要的。\n");
        content.append("服务提供商：我们可能聘请受合同约束的第三方服务商（如数据分析平台Crashlytics，云服务提供商）来协助我们，");
        content.append("他们仅能接触为完成其服务所必需的信息，并被禁止将信息用于其他任何目的。\n\n");
        
        content.append("4. 数据安全\n");
        content.append("我们实施了各种安全措施，包括数据加密（静态和传输中），以保护您的个人信息免遭未经授权的访问、更改、披露或销毁。\n\n");
        
        content.append("5. 数据保留\n");
        content.append("只要您的账户存在，我们就会保留您的信息。如果您希望删除账户，您可以在应用内直接操作，");
        content.append("或通过 keji_support@163.com 联系我们。账户删除后，您的所有个人数据将从我们的服务器中永久清除。\n\n");
        
        content.append("6. 您的权利\n");
        content.append("您有权：\n");
        content.append("访问我们持有的关于您的个人信息。\n");
        content.append("更正不准确的个人信息。\n");
        content.append("删除您的个人信息。\n");
        content.append("限制或反对我们处理您的个人信息。\n");
        content.append("随时通过设备权限设置撤销对我们收集数据的同意。\n\n");
        
        content.append("7. 儿童隐私\n");
        content.append("我们的服务不面向13岁以下的个人。如果我们发现收集了13岁以下儿童的信息，我们会立即删除。\n\n");
        
        content.append("8. 本政策的变更\n");
        content.append("我们可能会不时更新本政策。更新后，我们会在应用内发布新的隐私政策，并更新顶部的\"最后更新\"日期。");
        content.append("请您定期查阅。\n\n");
        
        content.append("9. 联系我们\n");
        content.append("如果您对本隐私政策有任何疑问、意见或建议，请通过以下方式联系我们：\n");
        content.append("克己开发团队\n");
        content.append("电子邮箱：keji_support@163.com\n");
        
        return content.toString();
    }
}

