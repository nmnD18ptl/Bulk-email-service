package com.bulkemail.pro.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.xbill.DNS.*;

import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class EmailValidationService {

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    public boolean isValidSyntax(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public boolean hasMxRecord(String email) {
        try {
            String domain = email.substring(email.indexOf('@') + 1);
            Lookup lookup = new Lookup(domain, Type.MX);
            lookup.run();
            if (lookup.getResult() == Lookup.SUCCESSFUL) {
                return lookup.getAnswers() != null && lookup.getAnswers().length > 0;
            }
            // Fallback: check A record
            Lookup aLookup = new Lookup(domain, Type.A);
            aLookup.run();
            return aLookup.getResult() == Lookup.SUCCESSFUL;
        } catch (Exception e) {
            log.debug("MX record check failed for {}: {}", email, e.getMessage());
            return false;
        }
    }

    public ValidationResult validate(String email) {
        if (!isValidSyntax(email)) {
            return new ValidationResult(false, false, "Invalid email syntax");
        }
        boolean hasMx = hasMxRecord(email);
        return new ValidationResult(true, hasMx, hasMx ? "Valid" : "No MX record found");
    }

    public record ValidationResult(boolean syntaxValid, boolean mxValid, String message) {
        public boolean isFullyValid() {
            return syntaxValid && mxValid;
        }
    }
}
