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
        
        privacyData.put("title", "克己隐私政策");
        privacyData.put("lastUpdated", "2025.10.25");
        privacyData.put("content", buildPrivacyContent());
        
        return ApiResponse.success(privacyData);
    }
    
    /**
     * 构建隐私政策内容
     */
    private String buildPrivacyContent() {
        StringBuilder content = new StringBuilder();
        
        content.append("克己隐私政策\n");
        content.append("更新日期：2025年10月25日\n\n");
        
        content.append("克己（以下称“我们”或“本应用”）理解隐私信息对你的重要性，并承诺保护你的隐私。");
        content.append("本政策旨在说明我们如何收集、使用、存储和共享你的个人信息，以及你如何行使隐私权利。");
        content.append("使用我们的服务前请仔细阅读并理解本政策。\n\n");
        
        content.append("1. 我们收集的信息\n");
        content.append("我们遵循“最小化原则”，仅收集维持应用核心功能所必需的信息。\n\n");
        
        content.append("a) 你主动提供的信息：\n\n");
        content.append("账户信息（如注册）：当你创建账户时，我们可能收集经过哈希处理的匿名标识（强烈推荐），");
        content.append("或仅收集完成服务所需的昵称与密码。为最大化匿名性，我们不建议收集邮箱或手机号。\n\n");
        content.append("用户生成内容：你在应用内主动记录的内容，如康复日记、情绪追踪、社区帖子（如有）等。");
        content.append("该数据仅用于设备间同步以及为你提供个性化服务。\n\n");
        
        content.append("b) 我们自动收集的信息：\n\n");
        content.append("使用数据：我们可能收集你访问的功能、使用时长、点击记录等匿名化信息，");
        content.append("用于整体数据分析与产品改进，且无法关联到特定个人。\n\n");
        content.append("设备信息：为保障服务安全与兼容性，我们可能收集设备类型、系统版本、匿名设备标识等。\n\n");
        
        content.append("c) 敏感信息：\n\n");
        content.append("我们理解你记录的康复数据、情绪日志等可能属于敏感个人信息。");
        content.append("我们承诺绝不将其用于其他目的，仅在获得你明确同意后用于提供核心康复支持服务。\n\n");
        
        content.append("2. 我们如何使用你的信息\n");
        content.append("用于提供并维护服务，确保应用正常运行。\n");
        content.append("用于个性化体验，向你展示相关激励内容与进展报告。\n");
        content.append("用于匿名化分析，以了解整体用户行为并优化功能与服务。\n");
        content.append("用于与您沟通，例如发送服务更新通知（你可选择退出）。\n\n");
        
        content.append("3. 信息共享与披露\n");
        content.append("我们绝不会出售、交易或出租你的可识别个人信息给任何第三方。仅在以下极少数情况共享：\n\n");
        content.append("获得你的明确同意。\n");
        content.append("法律合规：如我们认为披露信息对遵守法律、法规、传票或法院命令是必要的。\n");
        content.append("服务提供商：我们可能委托签订保密协议的第三方服务提供商（如 Crashlytics、云服务商等）协助完成服务。");
        content.append("他们仅可访问完成服务所需的信息，且不得用于其他目的。\n\n");
        
        content.append("4. 数据安全\n");
        content.append("我们采取多种安全措施，包括数据传输与存储加密，防止未经授权的访问、篡改、披露或销毁。\n\n");
        
        content.append("5. 数据保留\n");
        content.append("我们会在你的账号存在期间保留信息。若你希望删除账号，可在应用内直接操作，");
        content.append("或联系 keji_support@163.com。账号删除后，你的个人数据将从服务器永久移除。\n\n");
        
        content.append("6. 你的权利\n");
        content.append("你有权：\n");
        content.append("访问我们持有的个人信息。\n");
        content.append("更正不准确的个人信息。\n");
        content.append("删除个人信息。\n");
        content.append("限制或反对我们处理个人信息。\n");
        content.append("通过设备权限设置随时撤回数据收集同意。\n\n");
        
        content.append("7. 儿童隐私\n");
        content.append("我们的服务不面向13岁以下人群。如果发现我们收集了13岁以下儿童信息，将立即删除。\n\n");
        
        content.append("8. 政策变更\n");
        content.append("我们可能不时更新本政策。更新后会在应用内发布新隐私政策，并更新顶部的“更新日期”。");
        content.append("请定期查看。\n\n");

        content.append("9. 自启动或关联启动说明\n");
        content.append("• 小组件：本应用支持桌面小组件功能，采用 androidx.glance.appwidget（Jetpack Glance）实现。");
        content.append("该能力底层基于 Android AppWidget 机制，需监听系统小组件更新广播（如 android.appwidget.action.APPWIDGET_UPDATE），由系统在用户添加小组件后触发刷新流程。刷新是否执行及执行时机由系统控制；");
        content.append("当系统触发小组件更新时，应用进程可能被系统唤起以完成小组件数据与界面更新。本能力仅用于桌面小组件展示与更新，不用于无关场景。\n\n");
        
        content.append("10. 联系我们\n");
        content.append("如对本政策有疑问、评论或建议，请联系：\n");
        content.append("克己开发团队\n");
        content.append("邮箱：keji_support@163.com\n");
        
        return content.toString();
    }
}
