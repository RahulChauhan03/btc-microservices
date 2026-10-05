package com.btc.userservice.mail;

import org.springframework.web.util.HtmlUtils;

/** Content of the reset email: branding, the link, its lifetime and an ignore notice. No account data. */
final class PasswordResetEmail {

    private PasswordResetEmail() {
    }

    static String text(String link, long minutes) {
        return """
                BTC Flow

                We received a request to reset the password for your BTC Flow account.

                Choose a new password here (the link works once and expires in %d minutes):
                %s

                If you didn't ask to reset your password, you can ignore this email; your password will not change.
                """.formatted(minutes, link);
    }

    static String html(String link, long minutes) {
        String href = HtmlUtils.htmlEscape(link);
        return """
                <!doctype html>
                <html><body style="margin:0;padding:24px;background:#f5f6f8;font-family:Arial,Helvetica,sans-serif;color:#111827">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr><td align="center">
                    <table role="presentation" width="100%%" style="max-width:480px;background:#ffffff;border:1px solid #e3e6eb;border-radius:12px" cellpadding="0" cellspacing="0">
                      <tr><td style="padding:20px 28px;background:#0b1f3a;border-radius:12px 12px 0 0;color:#ffffff;font-size:18px;font-weight:bold">BTC Flow</td></tr>
                      <tr><td style="padding:28px">
                        <h1 style="margin:0 0 12px;font-size:20px">Reset your password</h1>
                        <p style="margin:0 0 20px;line-height:1.5;color:#4b5565">We received a request to reset the password for your BTC Flow account. Use the button below to choose a new one.</p>
                        <p style="margin:0 0 20px"><a href="%s" style="display:inline-block;padding:12px 20px;background:#2350d8;color:#ffffff;text-decoration:none;border-radius:8px;font-weight:bold">Choose a new password</a></p>
                        <p style="margin:0 0 12px;font-size:14px;line-height:1.5;color:#4b5565">This link works once and expires in %d minutes.</p>
                        <p style="margin:0;font-size:14px;line-height:1.5;color:#6b7383">If you didn't ask to reset your password, you can ignore this email; your password will not change.</p>
                      </td></tr>
                    </table>
                  </td></tr></table>
                </body></html>
                """.formatted(href, minutes);
    }
}
