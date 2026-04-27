package com.bulkemail.pro.config;

import com.bulkemail.pro.model.entity.AppSetting;
import com.bulkemail.pro.model.entity.Template;
import com.bulkemail.pro.repository.AppSettingRepository;
import com.bulkemail.pro.repository.TemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final TemplateRepository templateRepository;
    private final AppSettingRepository settingRepository;

    @Override
    public void run(String... args) {
        initSettings();
        initTemplates();
        log.info("Data initialization complete");
    }

    private void initSettings() {
        if (settingRepository.count() == 0) {
            settingRepository.save(new AppSetting("tracking.base.url", "http://localhost:8080", "tracking", "Base URL for tracking links"));
            settingRepository.save(new AppSetting("unsubscribe.base.url", "http://localhost:8080", "tracking", "Base URL for unsubscribe links"));
            settingRepository.save(new AppSetting("batch.default.size", "100", "sending", "Default batch size"));
            settingRepository.save(new AppSetting("batch.default.delay", "60", "sending", "Default delay between batches (seconds)"));
            settingRepository.save(new AppSetting("inter.email.delay", "200", "sending", "Delay between individual emails (ms)"));
            settingRepository.save(new AppSetting("company.name", "Your Company", "general", "Company name for email footer"));
            settingRepository.save(new AppSetting("company.address", "123 Main St, City, Country", "general", "Physical address (CAN-SPAM required)"));
            settingRepository.save(new AppSetting("default.from.name", "Your Name", "general", "Default sender name"));
            settingRepository.save(new AppSetting("default.from.email", "noreply@yourdomain.com", "general", "Default sender email"));
        }
    }

    private void initTemplates() {
        if (templateRepository.findByIsBuiltIn(true).isEmpty()) {
            createWelcomeTemplate();
            createNewsletterTemplate();
            createPromotionTemplate();
            createReminderTemplate();
            createProductLaunchTemplate();
            log.info("Built-in email templates created");
        }
    }

    private void createWelcomeTemplate() {
        Template t = new Template();
        t.setName("Welcome Email");
        t.setCategory("Welcome");
        t.setDescription("Welcome new subscribers to your list");
        t.setSubject("Welcome to {{Company}}, {{FirstName}}!");
        t.setBuiltIn(true);
        t.setHtmlContent("""
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Welcome</title></head>
            <body style="margin:0;padding:0;background-color:#f4f4f4;font-family:Arial,sans-serif;">
            <table width="100%" cellpadding="0" cellspacing="0" style="background-color:#f4f4f4;padding:20px;">
              <tr><td align="center">
                <table width="600" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;">
                  <tr><td style="background:#3B82F6;padding:40px;text-align:center;">
                    <h1 style="color:#ffffff;margin:0;font-size:28px;">Welcome, {{FirstName}}!</h1>
                  </td></tr>
                  <tr><td style="padding:40px;">
                    <p style="font-size:16px;color:#333;line-height:1.6;">Hi {{FirstName}},</p>
                    <p style="font-size:16px;color:#333;line-height:1.6;">Thank you for joining us! We're excited to have you on board.</p>
                    <p style="font-size:16px;color:#333;line-height:1.6;">As a member, you'll be the first to know about our latest updates, exclusive offers, and more.</p>
                    <div style="text-align:center;margin:30px 0;">
                      <a href="#" style="background:#3B82F6;color:#ffffff;padding:14px 30px;text-decoration:none;border-radius:6px;font-size:16px;font-weight:bold;">Get Started</a>
                    </div>
                    <p style="font-size:14px;color:#999;text-align:center;margin-top:30px;">
                      If you no longer wish to receive emails, <a href="{{UnsubscribeLink}}" style="color:#999;">unsubscribe here</a>.
                    </p>
                  </td></tr>
                  <tr><td style="background:#f8f8f8;padding:20px;text-align:center;">
                    <p style="color:#999;font-size:12px;margin:0;">© {{CurrentYear}} {{Company}} | 123 Main St, City, Country</p>
                  </td></tr>
                </table>
              </td></tr>
            </table>
            </body></html>
            """);
        t.setTextContent("Welcome {{FirstName}}! Thank you for joining us. To unsubscribe: {{UnsubscribeLink}}");
        templateRepository.save(t);
    }

    private void createNewsletterTemplate() {
        Template t = new Template();
        t.setName("Monthly Newsletter");
        t.setCategory("Newsletter");
        t.setDescription("Professional monthly newsletter template");
        t.setSubject("{{Company}} Newsletter - {{CustomField1}}");
        t.setBuiltIn(true);
        t.setHtmlContent("""
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
            <body style="margin:0;padding:0;background:#f0f0f0;font-family:Georgia,serif;">
            <table width="100%" cellpadding="0" cellspacing="0" style="padding:20px;">
              <tr><td align="center">
                <table width="600" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;">
                  <tr><td style="background:#1a1a2e;padding:30px 40px;border-bottom:4px solid #e94560;">
                    <h1 style="color:#e94560;margin:0;font-size:24px;letter-spacing:2px;">NEWSLETTER</h1>
                    <p style="color:#aaa;margin:5px 0 0;font-size:14px;">{{CustomField1}}</p>
                  </td></tr>
                  <tr><td style="padding:40px;">
                    <p style="font-size:16px;color:#333;line-height:1.8;">Dear {{FirstName}},</p>
                    <h2 style="color:#1a1a2e;border-bottom:2px solid #e94560;padding-bottom:10px;">Top Story</h2>
                    <p style="font-size:15px;color:#555;line-height:1.8;">Add your main newsletter content here. Share updates, news, and valuable insights with your subscribers.</p>
                    <h2 style="color:#1a1a2e;border-bottom:2px solid #e94560;padding-bottom:10px;">What's New</h2>
                    <p style="font-size:15px;color:#555;line-height:1.8;">Share your latest products, services, or announcements here.</p>
                    <div style="background:#f8f8f8;border-left:4px solid #e94560;padding:20px;margin:20px 0;">
                      <p style="margin:0;font-style:italic;color:#555;">"Share an inspirational quote or key message here."</p>
                    </div>
                    <p style="font-size:12px;color:#999;text-align:center;margin-top:30px;border-top:1px solid #eee;padding-top:20px;">
                      © {{CurrentYear}} {{Company}} | <a href="{{UnsubscribeLink}}" style="color:#999;">Unsubscribe</a>
                    </p>
                  </td></tr>
                </table>
              </td></tr>
            </table>
            </body></html>
            """);
        t.setTextContent("Dear {{FirstName}}, Thank you for reading our newsletter. To unsubscribe: {{UnsubscribeLink}}");
        templateRepository.save(t);
    }

    private void createPromotionTemplate() {
        Template t = new Template();
        t.setName("Special Promotion");
        t.setCategory("Promotion");
        t.setDescription("Eye-catching promotional email with CTA");
        t.setSubject("Exclusive Offer for {{FirstName}} - Don't Miss Out!");
        t.setBuiltIn(true);
        t.setHtmlContent("""
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
            <body style="margin:0;padding:0;background:#fff9e6;font-family:Arial,sans-serif;">
            <table width="100%" cellpadding="0" cellspacing="0" style="padding:20px;">
              <tr><td align="center">
                <table width="600" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.1);">
                  <tr><td style="background:linear-gradient(135deg,#FF6B6B,#FF8E53);padding:50px 40px;text-align:center;">
                    <p style="color:#ffe;font-size:14px;text-transform:uppercase;letter-spacing:3px;margin:0 0 10px;">Limited Time Offer</p>
                    <h1 style="color:#ffffff;font-size:48px;margin:0;font-weight:900;">{{CustomField1}}</h1>
                    <p style="color:#ffe;font-size:18px;margin:10px 0 0;">OFF YOUR NEXT PURCHASE</p>
                  </td></tr>
                  <tr><td style="padding:40px;text-align:center;">
                    <p style="font-size:18px;color:#333;">Hi {{FirstName}}, this exclusive offer is just for you!</p>
                    <p style="font-size:15px;color:#666;line-height:1.7;">Don't miss your chance to save big. This offer expires soon.</p>
                    <div style="background:#f8f8f8;border:2px dashed #FF6B6B;border-radius:8px;padding:20px;margin:25px 0;">
                      <p style="margin:0 0 5px;font-size:14px;color:#999;">Use code:</p>
                      <p style="margin:0;font-size:28px;font-weight:bold;color:#FF6B6B;letter-spacing:4px;">{{CustomField2}}</p>
                    </div>
                    <a href="#" style="background:#FF6B6B;color:#ffffff;padding:16px 40px;text-decoration:none;border-radius:50px;font-size:18px;font-weight:bold;display:inline-block;">Shop Now →</a>
                    <p style="font-size:12px;color:#bbb;margin-top:30px;">
                      <a href="{{UnsubscribeLink}}" style="color:#bbb;">Unsubscribe</a> | © {{CurrentYear}} {{Company}}
                    </p>
                  </td></tr>
                </table>
              </td></tr>
            </table>
            </body></html>
            """);
        t.setTextContent("Hi {{FirstName}}, You have an exclusive offer! Use code {{CustomField2}}. Unsubscribe: {{UnsubscribeLink}}");
        templateRepository.save(t);
    }

    private void createReminderTemplate() {
        Template t = new Template();
        t.setName("Follow-up Reminder");
        t.setCategory("Reminder");
        t.setDescription("Professional follow-up or reminder email");
        t.setSubject("Following up, {{FirstName}} - {{CustomField1}}");
        t.setBuiltIn(true);
        t.setHtmlContent("""
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
            <body style="margin:0;padding:0;background:#f5f5f5;font-family:Arial,sans-serif;">
            <table width="100%" cellpadding="0" cellspacing="0" style="padding:20px;">
              <tr><td align="center">
                <table width="600" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;">
                  <tr><td style="padding:40px;">
                    <p style="font-size:16px;color:#333;line-height:1.7;">Hi {{FirstName}},</p>
                    <p style="font-size:16px;color:#333;line-height:1.7;">I wanted to follow up on {{CustomField1}}. I noticed you haven't had a chance to take action yet, and I wanted to make sure you didn't miss out.</p>
                    <div style="border-left:4px solid #3B82F6;padding:15px 20px;background:#f0f7ff;margin:25px 0;border-radius:0 8px 8px 0;">
                      <p style="margin:0;font-size:15px;color:#333;font-weight:bold;">Quick reminder:</p>
                      <p style="margin:8px 0 0;font-size:15px;color:#555;">{{CustomField2}}</p>
                    </div>
                    <p style="font-size:15px;color:#555;line-height:1.7;">If you have any questions, simply reply to this email. I'm here to help!</p>
                    <div style="text-align:center;margin:30px 0;">
                      <a href="#" style="background:#3B82F6;color:#ffffff;padding:14px 30px;text-decoration:none;border-radius:6px;font-size:16px;font-weight:bold;">Take Action Now</a>
                    </div>
                    <p style="font-size:12px;color:#bbb;text-align:center;margin-top:20px;">
                      © {{CurrentYear}} {{Company}} | <a href="{{UnsubscribeLink}}" style="color:#bbb;">Unsubscribe</a>
                    </p>
                  </td></tr>
                </table>
              </td></tr>
            </table>
            </body></html>
            """);
        t.setTextContent("Hi {{FirstName}}, Following up on {{CustomField1}}. {{CustomField2}}. Unsubscribe: {{UnsubscribeLink}}");
        templateRepository.save(t);
    }

    private void createProductLaunchTemplate() {
        Template t = new Template();
        t.setName("Product Launch");
        t.setCategory("Product Launch");
        t.setDescription("Announce a new product or service launch");
        t.setSubject("Introducing {{CustomField1}} - Now Available!");
        t.setBuiltIn(true);
        t.setHtmlContent("""
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
            <body style="margin:0;padding:0;background:#0f0f1a;font-family:Arial,sans-serif;">
            <table width="100%" cellpadding="0" cellspacing="0" style="padding:20px;">
              <tr><td align="center">
                <table width="600" cellpadding="0" cellspacing="0" style="background:#1a1a2e;border-radius:12px;overflow:hidden;">
                  <tr><td style="padding:50px 40px;text-align:center;">
                    <p style="color:#8B5CF6;font-size:12px;text-transform:uppercase;letter-spacing:4px;margin:0 0 20px;">New Launch</p>
                    <h1 style="color:#ffffff;font-size:36px;margin:0 0 10px;font-weight:900;">{{CustomField1}}</h1>
                    <p style="color:#a0a0b0;font-size:18px;margin:0 0 30px;line-height:1.6;">{{CustomField2}}</p>
                    <div style="background:#2a2a3e;border-radius:10px;padding:30px;margin:20px 0;text-align:left;">
                      <p style="color:#8B5CF6;font-weight:bold;margin:0 0 15px;font-size:14px;text-transform:uppercase;">Key Features</p>
                      <p style="color:#ccc;margin:8px 0;font-size:15px;">✓ Feature 1 - Add your key benefit</p>
                      <p style="color:#ccc;margin:8px 0;font-size:15px;">✓ Feature 2 - Add your key benefit</p>
                      <p style="color:#ccc;margin:8px 0;font-size:15px;">✓ Feature 3 - Add your key benefit</p>
                    </div>
                    <a href="#" style="background:#8B5CF6;color:#ffffff;padding:16px 40px;text-decoration:none;border-radius:50px;font-size:18px;font-weight:bold;display:inline-block;margin-top:10px;">Learn More →</a>
                    <p style="color:#555;font-size:12px;margin-top:40px;">
                      © {{CurrentYear}} {{Company}} | <a href="{{UnsubscribeLink}}" style="color:#555;">Unsubscribe</a>
                    </p>
                  </td></tr>
                </table>
              </td></tr>
            </table>
            </body></html>
            """);
        t.setTextContent("Introducing {{CustomField1}}! {{CustomField2}}. Learn more today. Unsubscribe: {{UnsubscribeLink}}");
        templateRepository.save(t);
    }
}
