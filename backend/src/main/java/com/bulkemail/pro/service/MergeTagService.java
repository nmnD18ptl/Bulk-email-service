package com.bulkemail.pro.service;

import com.bulkemail.pro.model.entity.Contact;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class MergeTagService {

    public String merge(String template, Contact contact, String unsubscribeUrl, String trackingBaseUrl) {
        Map<String, String> vars = new HashMap<>();
        vars.put("{{FirstName}}", nvl(contact.getFirstName()));
        vars.put("{{LastName}}", nvl(contact.getLastName()));
        vars.put("{{FullName}}", contact.getFullName());
        vars.put("{{Email}}", nvl(contact.getEmail()));
        vars.put("{{Company}}", nvl(contact.getCompany()));
        vars.put("{{Country}}", nvl(contact.getCountry()));
        vars.put("{{Phone}}", nvl(contact.getPhone()));
        vars.put("{{CustomField1}}", nvl(contact.getCustomField1()));
        vars.put("{{CustomField2}}", nvl(contact.getCustomField2()));
        vars.put("{{CustomField3}}", nvl(contact.getCustomField3()));
        vars.put("{{CustomField4}}", nvl(contact.getCustomField4()));
        vars.put("{{CustomField5}}", nvl(contact.getCustomField5()));
        vars.put("{{UnsubscribeLink}}", unsubscribeUrl);
        vars.put("{{CurrentYear}}", String.valueOf(java.time.Year.now().getValue()));

        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public String mergeSubject(String subject, Contact contact) {
        return merge(subject, contact, "", "");
    }

    public String wrapLinks(String html, String trackingId, String trackingBaseUrl) {
        // Wrap all links with tracking redirect
        return html.replaceAll(
            "href=\"(http[^\"]+)\"",
            "href=\"" + trackingBaseUrl + "/track/click/" + trackingId + "?url=$1\""
        );
    }

    public String addTrackingPixel(String html, String trackingId, String trackingBaseUrl) {
        String pixel = "<img src=\"" + trackingBaseUrl + "/track/open/" + trackingId +
                       "\" width=\"1\" height=\"1\" style=\"display:none\" alt=\"\" />";
        return html.replace("</body>", pixel + "</body>");
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }
}
