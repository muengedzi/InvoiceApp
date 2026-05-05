# test_email_config.py
import os
import smtplib
from email.mime.text import MIMEText
from dotenv import load_dotenv

# Load .env file
load_dotenv()

EMAIL_ADDRESS = os.getenv("EMAIL_ADDRESS")
EMAIL_PASSWORD = os.getenv("EMAIL_PASSWORD")

print(f"Email Address: {EMAIL_ADDRESS}")
print(f"Password length: {len(EMAIL_PASSWORD) if EMAIL_PASSWORD else 0}")

def test_smtp():
    try:
        # Connect to Gmail SMTP
        server = smtplib.SMTP("smtp.gmail.com", 587)
        server.set_debuglevel(1)  # Show debug output
        server.starttls()
        
        print(f"Attempting to login with {EMAIL_ADDRESS}...")
        server.login(EMAIL_ADDRESS, EMAIL_PASSWORD.replace(" ", ""))  # Remove spaces if any
        
        # Send test email
        msg = MIMEText("This is a test email from InvoiceApp")
        msg["Subject"] = "Test Email"
        msg["From"] = EMAIL_ADDRESS
        msg["To"] = EMAIL_ADDRESS
        
        server.sendmail(EMAIL_ADDRESS, EMAIL_ADDRESS, msg.as_string())
        server.quit()
        
        print("✅ Email sent successfully!")
        return True
        
    except Exception as e:
        print(f"❌ Error: {e}")
        return False

if __name__ == "__main__":
    test_smtp()