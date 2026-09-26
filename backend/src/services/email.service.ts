import { env } from '../config/env';
import * as net from 'net';
import * as tls from 'tls';

export interface SendEmailOptions {
  to: string;
  subject: string;
  text: string;
  html?: string;
}

export interface EmailDeliveryResult {
  success: boolean;
  messageId?: string;
  error?: string;
}

export interface SentEmailRecord {
  to: string;
  subject: string;
  text: string;
  html?: string;
  timestamp: Date;
}

class EmailService {
  private sentEmailsStore: SentEmailRecord[] = [];

  /**
   * For unit testing verification only
   */
  public getSentEmails(): SentEmailRecord[] {
    return [...this.sentEmailsStore];
  }

  public clearSentEmails(): void {
    this.sentEmailsStore = [];
  }

  /**
   * Main email sending dispatch method
   */
  public async sendEmail(options: SendEmailOptions): Promise<EmailDeliveryResult> {
    const provider = (env.OTP_EMAIL_PROVIDER || 'mock').toLowerCase();
    const isProduction = env.NODE_ENV === 'production';

    try {
      if (env.NODE_ENV === 'test') {
        return this.sendMockEmail(options);
      }

      if (provider === 'mock') {
        if (isProduction) {
          return {
            success: false,
            error: 'Mock email provider is disabled in production environment. Please configure a valid email provider (resend, sendgrid, smtp).'
          };
        }
        return this.sendMockEmail(options);
      } else if (provider === 'resend') {
        return await this.sendResendEmail(options);
      } else if (provider === 'sendgrid') {
        return await this.sendSendGridEmail(options);
      } else if (provider === 'smtp') {
        return await this.sendSmtpEmail(options);
      } else {
        if (isProduction) {
          return {
            success: false,
            error: `Unsupported or unconfigured email provider '${provider}' in production environment.`
          };
        }
        return this.sendMockEmail(options);
      }
    } catch (err: any) {
      return {
        success: false,
        error: err.message || 'Failed to deliver email'
      };
    }
  }

  /**
   * Formats and sends a standardized CampusVerse Verification OTP Email
   */
  public async sendOtpEmail(to: string, otp: string, purpose: string): Promise<EmailDeliveryResult> {
    const purposeLabel =
      purpose === 'PASSWORD_RESET'
        ? 'Password Reset'
        : purpose === 'ACCOUNT_RECOVERY'
        ? 'Account Recovery'
        : 'Email Verification';

    const subject = `CampusVerse — Your ${purposeLabel} Code`;

    const text = [
      'CampusVerse',
      '',
      `Your verification code: ${otp}`,
      '',
      `Expiration: ${env.OTP_EXPIRY_MINUTES} minutes`,
      '',
      'Security warning: Do not share this code with anyone. CampusVerse team members will never ask for your verification code.',
      '',
      'If you did not request this code, please ignore this email.'
    ].join('\n');

    const html = `
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f7f1ff; margin: 0; padding: 24px; color: #1c192c; }
    .container { max-width: 520px; margin: 0 auto; background: #ffffff; border-radius: 16px; padding: 32px; box-shadow: 0 4px 16px rgba(0,0,0,0.06); }
    .logo { font-size: 24px; font-weight: bold; color: #5341cd; margin-bottom: 20px; }
    .heading { font-size: 20px; font-weight: 600; margin-bottom: 12px; }
    .code-box { background-color: #f1ebff; border-radius: 12px; padding: 20px; text-align: center; margin: 24px 0; }
    .code { font-size: 36px; font-weight: bold; letter-spacing: 8px; color: #5341cd; font-family: monospace; }
    .expiry { font-size: 14px; color: #58595e; margin-top: 8px; }
    .warning { background-color: #fff9eb; border-left: 4px solid #f59e0b; padding: 12px 16px; border-radius: 6px; font-size: 13px; color: #78350f; margin: 20px 0; }
    .footer { font-size: 12px; color: #787586; text-align: center; margin-top: 32px; border-top: 1px solid #e6dffa; padding-top: 16px; }
  </style>
</head>
<body>
  <div class="container">
    <div class="logo">CampusVerse</div>
    <div class="heading">${purposeLabel} Code</div>
    <p>Please use the following 6-digit verification code to complete your ${purposeLabel.toLowerCase()} process on CampusVerse:</p>
    <div class="code-box">
      <div class="code">${otp}</div>
      <div class="expiry">Expires in ${env.OTP_EXPIRY_MINUTES} minutes</div>
    </div>
    <div class="warning">
      <strong>Security Warning:</strong> Do not share this code with anyone. CampusVerse will never ask for this code.
    </div>
    <p style="font-size: 13px; color: #58595e;">If you did not request this verification, you can safely ignore this email.</p>
    <div class="footer">
      &copy; ${new Date().getFullYear()} CampusVerse Inc. All rights reserved.
    </div>
  </div>
</body>
</html>
    `.trim();

    return this.sendEmail({ to, subject, text, html });
  }

  // --- Provider Implementations ---

  private sendMockEmail(options: SendEmailOptions): EmailDeliveryResult {
    this.sentEmailsStore.push({
      to: options.to,
      subject: options.subject,
      text: options.text,
      html: options.html,
      timestamp: new Date()
    });

    return {
      success: true,
      messageId: `mock_${Date.now()}_${Math.floor(Math.random() * 10000)}`
    };
  }

  private async sendResendEmail(options: SendEmailOptions): Promise<EmailDeliveryResult> {
    if (!env.RESEND_API_KEY) {
      throw new Error('RESEND_API_KEY is not configured');
    }

    const response = await fetch('https://api.resend.com/emails', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${env.RESEND_API_KEY}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        from: `${env.OTP_FROM_NAME} <${env.OTP_FROM_EMAIL}>`,
        to: [options.to],
        subject: options.subject,
        text: options.text,
        html: options.html
      })
    });

    if (!response.ok) {
      const errText = await response.text();
      return { success: false, error: `Resend API error: ${errText}` };
    }

    const data: any = await response.json();
    return { success: true, messageId: data.id };
  }

  private async sendSendGridEmail(options: SendEmailOptions): Promise<EmailDeliveryResult> {
    if (!env.SENDGRID_API_KEY) {
      throw new Error('SENDGRID_API_KEY is not configured');
    }

    const response = await fetch('https://api.sendgrid.com/v3/mail/send', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${env.SENDGRID_API_KEY}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        personalizations: [{ to: [{ email: options.to }] }],
        from: { email: env.OTP_FROM_EMAIL, name: env.OTP_FROM_NAME },
        subject: options.subject,
        content: [
          { type: 'text/plain', value: options.text },
          ...(options.html ? [{ type: 'text/html', value: options.html }] : [])
        ]
      })
    });

    if (!response.ok) {
      const errText = await response.text();
      return { success: false, error: `SendGrid API error: ${errText}` };
    }

    return { success: true };
  }

  private async sendSmtpEmail(options: SendEmailOptions): Promise<EmailDeliveryResult> {
    if (!env.SMTP_HOST) {
      throw new Error('SMTP_HOST is not configured');
    }

    return new Promise((resolve, reject) => {
      const isTls = env.SMTP_SECURE || env.SMTP_PORT === 465;
      const socket = isTls
        ? tls.connect({ host: env.SMTP_HOST, port: env.SMTP_PORT, rejectUnauthorized: false })
        : net.connect({ host: env.SMTP_HOST, port: env.SMTP_PORT });

      let stage = 0;
      let buffer = '';

      const sendLine = (line: string) => {
        socket.write(line + '\r\n');
      };

      socket.on('data', (chunk: any) => {
        buffer += chunk.toString();
        const lines = buffer.split('\r\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (!line) continue;
          const code = parseInt(line.substring(0, 3), 10);

          if (stage === 0 && code === 220) {
            stage = 1;
            sendLine(`EHLO campusverse.edu`);
          } else if (stage === 1 && code === 250 && !line.startsWith('250-')) {
            if (env.SMTP_USER && env.SMTP_PASSWORD) {
              stage = 2;
              sendLine('AUTH LOGIN');
            } else {
              stage = 4;
              sendLine(`MAIL FROM:<${env.OTP_FROM_EMAIL}>`);
            }
          } else if (stage === 2 && code === 334) {
            stage = 3;
            sendLine(Buffer.from(env.SMTP_USER).toString('base64'));
          } else if (stage === 3 && code === 334) {
            stage = 4;
            sendLine(Buffer.from(env.SMTP_PASSWORD).toString('base64'));
          } else if (stage === 4 && (code === 235 || code === 250)) {
            stage = 5;
            sendLine(`MAIL FROM:<${env.OTP_FROM_EMAIL}>`);
          } else if (stage === 5 && code === 250) {
            stage = 6;
            sendLine(`RCPT TO:<${options.to}>`);
          } else if (stage === 6 && code === 250) {
            stage = 7;
            sendLine('DATA');
          } else if (stage === 7 && code === 354) {
            stage = 8;
            const emailData = [
              `From: ${env.OTP_FROM_NAME} <${env.OTP_FROM_EMAIL}>`,
              `To: <${options.to}>`,
              `Subject: ${options.subject}`,
              'MIME-Version: 1.0',
              'Content-Type: text/html; charset=UTF-8',
              '',
              options.html || options.text,
              '.'
            ].join('\r\n');
            sendLine(emailData);
          } else if (stage === 8 && code === 250) {
            stage = 9;
            sendLine('QUIT');
            socket.end();
            resolve({ success: true });
          } else if (code >= 400) {
            socket.destroy();
            resolve({ success: false, error: `SMTP server responded with error: ${line}` });
          }
        }
      });

      socket.on('error', (err: any) => {
        resolve({ success: false, error: `SMTP connection error: ${err.message}` });
      });

      socket.setTimeout(10000, () => {
        socket.destroy();
        resolve({ success: false, error: 'SMTP connection timeout' });
      });
    });
  }
}

export const emailService = new EmailService();
