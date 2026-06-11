# ============================================
# INVOICEAPP - FLASK BACKEND
# ============================================

import os
import sys
import decimal
import re
import time
import threading
import calendar as month_calendar
import bcrypt
import secrets
import smtplib
import imaplib
import json
import pdfkit
from datetime import datetime, timedelta
from functools import wraps
from pathlib import Path
from email import message_from_bytes
from email.header import decode_header
from email.utils import parseaddr, parsedate_to_datetime
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.mime.application import MIMEApplication
from invoice_tasks import InvoiceTaskManager, MAX_WORKERS

from flask import (
    Flask, request, jsonify, render_template,
    redirect, url_for, session, make_response
)
from flask_login import (
    LoginManager, login_user, logout_user, login_required, 
    UserMixin, current_user
)
from flask_cors import CORS
import pyodbc
from dotenv import load_dotenv

# Load environment variables
BASE_DIR = Path(__file__).resolve().parent
load_dotenv(BASE_DIR / '.env')

# ============================================
# APP CONFIGURATION
# ============================================
app = Flask(__name__, static_folder='static', static_url_path='/static')
app.secret_key = os.getenv('SECRET_KEY', 'your-secret-key-change-this')

app.config['STATIC_FOLDER'] = 'static'
app.config['UPLOAD_FOLDER'] = os.path.join('static', 'uploads')

task_manager = None

# Session configuration
app.config['SECRET_KEY'] = os.getenv('SECRET_KEY', 'your-secret-key-change-this')
app.config['SESSION_COOKIE_HTTPONLY'] = True
app.config['SESSION_COOKIE_SAMESITE'] = 'Lax'
app.config['SESSION_COOKIE_SECURE'] = False
app.config['REMEMBER_COOKIE_HTTPONLY'] = True
app.config['REMEMBER_COOKIE_DURATION'] = timedelta(days=7)
app.config['SESSION_PERMANENT'] = True
app.config['PERMANENT_SESSION_LIFETIME'] = timedelta(days=7)

# CORS configuration
CORS(app, 
     supports_credentials=True,
     origins=[
         "http://localhost:5000",
         "http://127.0.0.1:5000",
         "http://10.0.0.111:5000",
         "http://10.0.0.150:5000",
         "http://10.0.0.150:8080",
         "http://192.168.8.150:5000",
         "http://192.168.8.150:8080" 
     ],
     allow_headers=["Content-Type", "Authorization", "X-Requested-With", "Accept", "Cookie", "X-CSRFToken"],
     expose_headers=["Set-Cookie", "Cookie", "Content-Disposition"],
     methods=["GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"],
     max_age=3600)

# PDF Configuration - wkhtmltopdf path
WKHTMLTOPDF_PATH = r"C:\Program Files\wkhtmltopdf\bin\wkhtmltopdf.exe"
pdfkit_config = pdfkit.configuration(wkhtmltopdf=WKHTMLTOPDF_PATH)

# Database Connection
DB_CONFIG = {
    'driver': os.getenv('DB_DRIVER', '{ODBC Driver 17 for SQL Server}'),
    'server': os.getenv('DB_SERVER', 'DESKTOP-HEACHMT\\SQLEXPRESS'),
    'database': os.getenv('DB_NAME', 'InvoiceApp'),
    'trusted_connection': os.getenv('DB_TRUSTED_CONNECTION', 'yes'),
    'timeout': os.getenv('DB_CONNECTION_TIMEOUT', '10')
}

def get_db_connection():
    """Get database connection"""
    conn_str = (
        f"Driver={DB_CONFIG['driver']};"
        f"Server={DB_CONFIG['server']};"
        f"Database={DB_CONFIG['database']};"
        f"Trusted_Connection={DB_CONFIG['trusted_connection']};"
        f"Connection Timeout={DB_CONFIG['timeout']};"
    )
    return pyodbc.connect(conn_str)

# Email Configuration
SMTP_SERVER = "smtp.gmail.com"
SMTP_PORT = 587
EMAIL_ADDRESS = os.getenv("EMAIL_ADDRESS")
EMAIL_PASSWORD = os.getenv("EMAIL_PASSWORD")
IMAP_SERVER = os.getenv("IMAP_SERVER", "imap.gmail.com")
IMAP_PORT = int(os.getenv("IMAP_PORT", "993"))
INBOUND_WEBHOOK_SECRET = os.getenv("INBOUND_WEBHOOK_SECRET", "")

# ============================================
# DATABASE HELPER FUNCTIONS
# ============================================

def database_unavailable_response(error):
    print(f"Database connection error: {error}")
    return jsonify({
        "error": "Database is unavailable. Please make sure SQL Server (SQLEXPRESS) is running and try again."
    }), 503

def ensure_customer_company_columns(cursor):
    cursor.execute("""
        IF COL_LENGTH('dbo.customers', 'is_company') IS NULL
            ALTER TABLE dbo.customers ADD is_company BIT NOT NULL CONSTRAINT DF_customers_is_company DEFAULT 0
        IF COL_LENGTH('dbo.customers', 'company_registration_number') IS NULL
            ALTER TABLE dbo.customers ADD company_registration_number NVARCHAR(100) NULL
        IF COL_LENGTH('dbo.customers', 'vat_registered') IS NULL
            ALTER TABLE dbo.customers ADD vat_registered BIT NOT NULL CONSTRAINT DF_customers_vat_registered DEFAULT 0
        IF COL_LENGTH('dbo.customers', 'vat_number') IS NULL
            ALTER TABLE dbo.customers ADD vat_number NVARCHAR(100) NULL
        IF COL_LENGTH('dbo.customers', 'company_address') IS NULL
            ALTER TABLE dbo.customers ADD company_address NVARCHAR(500) NULL
    """)

def ensure_company_settings_columns(cursor):
    cursor.execute("""
        IF COL_LENGTH('dbo.company_settings', 'company_vat_number') IS NULL
            ALTER TABLE dbo.company_settings ADD company_vat_number NVARCHAR(100) NULL

        IF COL_LENGTH('dbo.company_settings', 'invoice_notes_default') IS NULL
            ALTER TABLE dbo.company_settings ADD invoice_notes_default NVARCHAR(MAX) NULL

        IF COL_LENGTH('dbo.company_settings', 'terms_conditions_default') IS NULL
            ALTER TABLE dbo.company_settings ADD terms_conditions_default NVARCHAR(MAX) NULL

        IF COL_LENGTH('dbo.company_settings', 'footer_text_default') IS NULL
            ALTER TABLE dbo.company_settings ADD footer_text_default NVARCHAR(MAX) NULL

        IF COL_LENGTH('dbo.company_settings', 'payment_terms_default') IS NULL
            ALTER TABLE dbo.company_settings ADD payment_terms_default NVARCHAR(MAX) NULL

        IF COL_LENGTH('dbo.company_settings', 'bank_account_details') IS NULL
            ALTER TABLE dbo.company_settings ADD bank_account_details NVARCHAR(MAX) NULL
    """)

def ensure_invoice_operational_columns(cursor):
    cursor.execute("""
        IF COL_LENGTH('dbo.invoices', 'payment_method') IS NULL
            ALTER TABLE dbo.invoices ADD payment_method NVARCHAR(100) NULL

        IF COL_LENGTH('dbo.invoices', 'recurring_template_id') IS NULL
            ALTER TABLE dbo.invoices ADD recurring_template_id INT NULL

        IF COL_LENGTH('dbo.invoices', 'is_generated_from_recurring') IS NULL
            ALTER TABLE dbo.invoices ADD is_generated_from_recurring BIT NOT NULL CONSTRAINT DF_invoices_generated_from_recurring DEFAULT 0

        IF COL_LENGTH('dbo.invoices', 'tax_type') IS NULL
            ALTER TABLE dbo.invoices ADD tax_type NVARCHAR(20) NOT NULL DEFAULT 'inclusive'
    """)

def ensure_recurring_tables(cursor):
    """Create recurring invoice template tables if they don't exist"""
    cursor.execute("""
        IF OBJECT_ID('dbo.recurring_invoice_templates', 'U') IS NULL
        BEGIN
            CREATE TABLE dbo.recurring_invoice_templates (
                id INT IDENTITY(1,1) PRIMARY KEY,
                user_id INT NOT NULL,
                customer_id INT NOT NULL,
                template_name NVARCHAR(200) NOT NULL,
                invoice_date_day INT NOT NULL,
                due_date_days INT NOT NULL,
                frequency NVARCHAR(20) NOT NULL,
                status NVARCHAR(20) NOT NULL DEFAULT 'active',
                tax_type NVARCHAR(20) NOT NULL DEFAULT 'inclusive',
                created_at DATETIME NOT NULL DEFAULT GETDATE(),
                updated_at DATETIME NOT NULL DEFAULT GETDATE(),
                FOREIGN KEY (user_id) REFERENCES users(id),
                FOREIGN KEY (customer_id) REFERENCES customers(id)
            )
        END
    """)
    
    cursor.execute("""
        IF OBJECT_ID('dbo.recurring_invoice_template_items', 'U') IS NULL
        BEGIN
            CREATE TABLE dbo.recurring_invoice_template_items (
                id INT IDENTITY(1,1) PRIMARY KEY,
                template_id INT NOT NULL,
                item_name NVARCHAR(200) NOT NULL,
                description NVARCHAR(MAX) NULL,
                quantity DECIMAL(18,2) NOT NULL DEFAULT 1,
                unit_price DECIMAL(18,2) NOT NULL DEFAULT 0,
                discount_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
                tax_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
                sort_order INT NOT NULL DEFAULT 0,
                FOREIGN KEY (template_id) REFERENCES recurring_invoice_templates(id) ON DELETE CASCADE
            )
        END
    """)

def ensure_email_sync_table(cursor):
    cursor.execute("""
        IF OBJECT_ID('dbo.email_sync_log', 'U') IS NULL
        BEGIN
            CREATE TABLE dbo.email_sync_log (
                id INT IDENTITY(1,1) PRIMARY KEY,
                user_id INT NOT NULL,
                message_id NVARCHAR(300) NOT NULL,
                invoice_id INT NULL,
                communication_id INT NULL,
                sender_email NVARCHAR(510) NULL,
                subject NVARCHAR(1000) NULL,
                received_at DATETIME NULL,
                processed_at DATETIME NOT NULL DEFAULT GETDATE(),
                CONSTRAINT UQ_email_sync_log_user_message UNIQUE (user_id, message_id)
            )
        END
    """)

def calculate_recurring_next_send_date(frequency, recurring_day=None):
    """Calculate next send date for a recurring invoice"""
    today = datetime.now().date()
    frequency = (frequency or 'monthly').lower()
    target_day = min(max(int(recurring_day or today.day), 1), 28)

    if frequency == 'weekly':
        return today + timedelta(days=7)
    if frequency == 'biweekly':
        return today + timedelta(days=14)
    if frequency == 'bimonthly':
        month = today.month + 2
        year = today.year + (month - 1) // 12
        month = ((month - 1) % 12) + 1
        day = min(target_day, month_calendar.monthrange(year, month)[1])
        return datetime(year, month, day).date()
    if frequency == 'quarterly':
        month = today.month + 3
        year = today.year + (month - 1) // 12
        month = ((month - 1) % 12) + 1
        day = min(target_day, month_calendar.monthrange(year, month)[1])
        return datetime(year, month, day).date()
    if frequency == 'yearly':
        return datetime(today.year + 1, today.month, min(target_day, 28)).date()

    # Default monthly
    next_date = datetime(today.year, today.month, target_day).date()
    if next_date <= today:
        month = today.month + 1
        year = today.year + (month - 1) // 12
        month = ((month - 1) % 12) + 1
        day = min(target_day, month_calendar.monthrange(year, month)[1])
        next_date = datetime(year, month, day).date()
    return next_date

# ============================================
# FLASK-LOGIN SETUP
# ============================================
login_manager = LoginManager()
login_manager.login_view = "login"
login_manager.init_app(app)

class User(UserMixin):
    def __init__(self, id, email, full_name, role):
        self.id = id
        self.email = email
        self.full_name = full_name
        self.role = role

@login_manager.user_loader
def load_user(user_id):
    try:
        conn = get_db_connection()
    except pyodbc.Error as e:
        print(f"Database connection error while loading user: {e}")
        return None

    cursor = conn.cursor()
    cursor.execute("SELECT id, email, full_name, role FROM users WHERE id = ? AND is_active = 1", (user_id,))
    user = cursor.fetchone()
    conn.close()
    
    if user:
        return User(user[0], user[1], user[2], user[3])
    return None

# ============================================
# DECORATORS
# ============================================
def admin_required(f):
    @wraps(f)
    def decorated_function(*args, **kwargs):
        if not current_user.is_authenticated or current_user.role != 'admin':
            return jsonify({"error": "Admin access required"}), 403
        return f(*args, **kwargs)
    return decorated_function

# ============================================
# AUTHENTICATION ROUTES
# ============================================

@app.route('/api/register', methods=['POST'])
def register():
    data = request.get_json()
    
    full_name = data.get('full_name', '').strip()
    email = data.get('email', '').strip().lower()
    password = data.get('password', '')
    business_name = data.get('business_name', '').strip()
    
    if not full_name or not email or not password:
        return jsonify({"error": "All fields are required"}), 400
    
    if len(password) < 8:
        return jsonify({"error": "Password must be at least 8 characters"}), 400
    
    password_hash = bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')
    verification_token = secrets.token_urlsafe(32)
    verification_expiry = datetime.now() + timedelta(hours=24)
    
    try:
        conn = get_db_connection()
    except pyodbc.Error as e:
        return database_unavailable_response(e)

    cursor = conn.cursor()
    
    try:
        cursor.execute("""
            INSERT INTO users (full_name, email, password_hash, business_name, 
                             email_verification_token, email_verification_expiry, 
                             created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, GETDATE(), GETDATE())
        """, (full_name, email, password_hash, business_name, verification_token, verification_expiry))
        
        conn.commit()
        send_verification_email(email, full_name, verification_token)
        
        return jsonify({
            "success": True,
            "message": "Registration successful. Please check your email for verification."
        }), 201
        
    except pyodbc.IntegrityError:
        return jsonify({"error": "Email already exists"}), 409
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()

@app.route('/api/login', methods=['POST'])
def login():
    data = request.get_json()
    email = data.get('email', '').strip().lower()
    password = data.get('password', '')
    
    if not email or not password:
        return jsonify({"error": "Email and password required"}), 400
    
    try:
        conn = get_db_connection()
    except pyodbc.Error as e:
        return database_unavailable_response(e)

    cursor = conn.cursor()
    
    cursor.execute("""
        SELECT id, email, full_name, password_hash, role, email_verified, is_active 
        FROM users WHERE email = ?
    """, (email,))
    
    user = cursor.fetchone()
    conn.close()
    
    if not user:
        return jsonify({"error": "Invalid credentials"}), 401
    
    user_id, user_email, full_name, password_hash, role, email_verified, is_active = user
    
    if not is_active:
        return jsonify({"error": "Account is deactivated"}), 401
    
    if not email_verified:
        return jsonify({"error": "Please verify your email before logging in"}), 401
    
    if not bcrypt.checkpw(password.encode('utf-8'), password_hash.encode('utf-8')):
        return jsonify({"error": "Invalid credentials"}), 401
    
    user_obj = User(user_id, user_email, full_name, role)
    login_user(user_obj, remember=True, duration=timedelta(days=7))
    session.permanent = True
    
    print(f"✅ Login successful for: {email}")
    
    return jsonify({
        "success": True,
        "user": {
            "id": user_id,
            "email": user_email,
            "full_name": full_name,
            "role": role
        }
    }), 200

@app.route('/api/logout', methods=['POST'])
@login_required
def logout():
    logout_user()
    return jsonify({"success": True}), 200

@app.route('/api/verify-email', methods=['POST'])
def verify_email():
    data = request.get_json()
    token = data.get('token')
    
    if not token:
        return jsonify({"error": "Token required"}), 400
    
    conn = get_db_connection()
    cursor = conn.cursor()
    
    cursor.execute("""
        SELECT id, email_verification_expiry 
        FROM users 
        WHERE email_verification_token = ? AND email_verified = 0
    """, (token,))
    
    user = cursor.fetchone()
    
    if not user:
        conn.close()
        return jsonify({"error": "Invalid or already used token"}), 400
    
    user_id, expiry = user
    
    if expiry and expiry < datetime.now():
        conn.close()
        return jsonify({"error": "Verification link has expired"}), 400
    
    cursor.execute("""
        UPDATE users 
        SET email_verified = 1, is_active = 1,
            email_verification_token = NULL, email_verification_expiry = NULL
        WHERE id = ?
    """, (user_id,))
    
    conn.commit()
    conn.close()
    
    return jsonify({"success": True, "message": "Email verified successfully"}), 200

def send_verification_email(to_email, to_name, token):
    try:
        verification_link = f"http://10.0.0.150:5000/verify-email-page?token={token}"
        app_link = f"invoiceapp://verify?token={token}"
        
        subject = "Verify Your Email - InvoiceApp"
        
        html_content = f"""
        <html>
        <body style="font-family: Arial, sans-serif;">
            <h2>Welcome to InvoiceApp, {to_name}!</h2>
            <p>Please verify your email address by clicking one of the links below:</p>
            
            <p><b>Option 1 - Open in App (Recommended for mobile):</b></p>
            <p><a href="{app_link}" style="background-color: #4CAF50; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;">Verify in App</a></p>
            
            <p><b>Option 2 - Open in Browser:</b></p>
            <p><a href="{verification_link}">Click here to verify in browser</a></p>
            
            <p>This link expires in 24 hours.</p>
            <p>If you didn't create an account with InvoiceApp, please ignore this email.</p>
            
            <hr>
            <p style="font-size: 12px; color: #666;">InvoiceApp - Invoice Management System</p>
        </body>
        </html>
        """
        
        msg = MIMEMultipart('alternative')
        msg["From"] = EMAIL_ADDRESS
        msg["To"] = to_email
        msg["Subject"] = subject
        msg.attach(MIMEText(html_content, "html"))
        
        with smtplib.SMTP(SMTP_SERVER, SMTP_PORT) as server:
            server.starttls()
            server.login(EMAIL_ADDRESS, EMAIL_PASSWORD)
            server.sendmail(EMAIL_ADDRESS, to_email, msg.as_string())
        
        return True
    except Exception:
        return False

# ============================================
# PASSWORD RESET ENDPOINTS
# ============================================

@app.route('/api/forgot-password', methods=['POST'])
def forgot_password():
    try:
        data = request.get_json()
        if not data:
            return jsonify({"error": "No data provided"}), 400

        email = data.get('email', '').strip().lower()
        
        if not email:
            return jsonify({"error": "Email is required"}), 400
        
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute("SELECT id, full_name, email FROM users WHERE email = ?", (email,))
        user = cursor.fetchone()
        
        if not user:
            conn.close()
            return jsonify({
                "success": True,
                "message": "If an account exists with that email, you will receive a password reset link."
            }), 200
        
        user_id, full_name, user_email = user
        
        reset_token = secrets.token_urlsafe(32)
        expiry_time = datetime.now() + timedelta(hours=1)
        
        cursor.execute("""
            UPDATE users 
            SET reset_token = ?, reset_token_expiry = ? 
            WHERE id = ?
        """, (reset_token, expiry_time, user_id))
        
        conn.commit()
        conn.close()
        
        send_password_reset_email(user_email, full_name, reset_token)
        
        return jsonify({
            "success": True,
            "message": "Password reset link sent to your email."
        }), 200

    except Exception as e:
        print(f"Forgot Password Error: {str(e)}")
        return jsonify({"error": "Internal server error"}), 500

@app.route('/api/reset-password', methods=['POST'])
def reset_password():
    try:
        data = request.get_json()
        if not data:
            return jsonify({"error": "No data provided"}), 400
        
        token = data.get('token', '').strip()
        new_password = data.get('new_password', '').strip()
        
        if not token or not new_password:
            return jsonify({"error": "Token and new password are required"}), 400
        
        if len(new_password) < 8:
            return jsonify({"error": "Password must be at least 8 characters long"}), 400
        
        if not re.search(r"\d", new_password) or not re.search(r"[!@#$%^&*(),.?\":{}|<>]", new_password):
            return jsonify({"error": "Password must include at least one number and one special character"}), 400
        
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute("""
            SELECT id, reset_token_expiry 
            FROM users 
            WHERE reset_token = ? AND reset_token_expiry > GETDATE()
        """, (token,))
        
        user = cursor.fetchone()
        
        if not user:
            conn.close()
            return jsonify({"error": "Invalid or expired reset token"}), 400
        
        user_id, token_expiry = user
        
        password_hash = bcrypt.hashpw(new_password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')
        
        cursor.execute("""
            UPDATE users
            SET password_hash = ?, 
                reset_token = NULL, 
                reset_token_expiry = NULL,
                updated_at = GETDATE()
            WHERE id = ?
        """, (password_hash, user_id))
        
        conn.commit()
        conn.close()
        
        return jsonify({
            "success": True,
            "message": "Password has been reset successfully. You can now login with your new password."
        }), 200
        
    except Exception as e:
        print(f"Reset password error: {e}")
        return jsonify({"error": "An error occurred. Please try again."}), 500

def send_password_reset_email(to_email, to_name, reset_token):
    try:
        reset_link_deep = f"invoiceapp://reset-password?token={reset_token}"
        reset_link_web = f"http://10.0.0.150:5000/reset-password?token={reset_token}"
        
        subject = "Reset Your Password - InvoiceApp"
        
        html_content = f"""
        <!DOCTYPE html>
        <html>
        <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto;">
            <div style="background: #f8f9fa; padding: 20px; border-radius: 10px;">
                <h2 style="color: #3498db; text-align: center;">Password Reset Request</h2>
                
                <p>Hi {to_name},</p>
                
                <p>We received a request to reset your password for your InvoiceApp account. If you didn't make this request, you can safely ignore this email.</p>
                
                <p>To reset your password using the mobile app, click the button below:</p>
                
                <div style="text-align: center; margin: 30px 0;">
                    <a href="{reset_link_deep}" 
                       style="background-color: #3498db; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; font-weight: bold; display: inline-block;">
                        Reset Password in App
                    </a>
                </div>
                
                <p>Or copy and paste this link into your browser:</p>
                <p style="background: #eee; padding: 10px; border-radius: 5px; word-break: break-all;">{reset_link_web}</p>
                
                <p>This link will expire in 1 hour for security reasons.</p>
                
                <p>Best regards,<br>
                InvoiceApp Team</p>
                
                <hr style="border: none; border-top: 1px solid #ddd; margin: 20px 0;">
                
                <p style="font-size: 12px; color: #666;">
                    If you didn't request a password reset, please ignore this email.
                </p>
            </div>
        </body>
        </html>
        """
        
        msg = MIMEMultipart('alternative')
        msg["From"] = EMAIL_ADDRESS
        msg["To"] = to_email
        msg["Subject"] = subject
        
        msg.attach(MIMEText(html_content, "html"))
        
        with smtplib.SMTP(SMTP_SERVER, SMTP_PORT) as server:
            server.starttls()
            server.login(EMAIL_ADDRESS, EMAIL_PASSWORD)
            server.sendmail(EMAIL_ADDRESS, to_email, msg.as_string())
        
        print(f"Password reset email sent to {to_email}")
        return True
        
    except Exception as e:
        print(f"Error sending password reset email: {e}")
        return False

@app.route('/reset-password', methods=['GET'])
def reset_password_page():
    token = request.args.get('token', '')
    
    if not token:
        return render_template('reset_password.html', token=None, error="No reset token provided. Please request a new password reset link.")
    
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute("""
            SELECT id FROM users 
            WHERE reset_token = ? AND reset_token_expiry > GETDATE()
        """, (token,))
        
        user = cursor.fetchone()
        conn.close()
        
        if not user:
            return render_template('reset_password.html', token=None, error="This reset link is invalid or has expired. Please request a new password reset.")
        
        return render_template('reset_password.html', token=token, error=None)
        
    except Exception as e:
        print(f"Error verifying token: {e}")
        return render_template('reset_password.html', token=token, error="An error occurred. Please try again.")

@app.route('/api/login-page', methods=['GET'])
def login_page():
    return render_template('login_redirect.html')

# ============================================
# RECURRING INVOICE TEMPLATE ENDPOINTS
# ============================================

@app.route('/api/recurring-templates', methods=['GET'])
@login_required
def get_recurring_templates():
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_recurring_tables(cursor)
    conn.commit()
    
    cursor.execute("""
        SELECT 
            rt.id, rt.user_id, rt.customer_id, rt.template_name,
            rt.invoice_date_day, rt.due_date_days, rt.frequency, rt.status,
            c.customer_name, rt.created_at, rt.updated_at, rt.tax_type
        FROM recurring_invoice_templates rt
        JOIN customers c ON rt.customer_id = c.id
        WHERE rt.user_id = ?
        ORDER BY rt.created_at DESC
    """, (current_user.id,))
    
    templates = []
    for row in cursor.fetchall():
        template_id = row[0]
        tax_type = row[11] or "inclusive"
        is_inclusive = tax_type == "inclusive"
        
        # Get items for this template to calculate total amount
        cursor.execute("""
            SELECT item_name, description, quantity, unit_price, discount_amount, tax_rate
            FROM recurring_invoice_template_items
            WHERE template_id = ?
            ORDER BY sort_order, id
        """, (template_id,))
        
        items = []
        subtotal = 0.0
        total_tax_amount = 0.0
        
        for item_row in cursor.fetchall():
            # Index positions based on SELECT order
            item_name = item_row[0]
            description = item_row[1] or ""
            quantity = float(item_row[2]) if item_row[2] else 1
            unit_price = float(item_row[3]) if item_row[3] else 0
            discount = float(item_row[4]) if item_row[4] else 0
            tax_rate = float(item_row[5]) if item_row[5] else 0
            
            item_amount = (quantity * unit_price) - discount
            
            item_tax = 0
            if is_inclusive:
                item_tax = item_amount - (item_amount / (1 + (tax_rate / 100)))
                subtotal += (item_amount - item_tax)
            else:
                item_tax = item_amount * (tax_rate / 100)
                subtotal += item_amount
            
            total_tax_amount += item_tax
            
            items.append({
                "item_name": item_name,
                "description": description,
                "quantity": quantity,
                "unit_price": unit_price,
                "discount_amount": discount,
                "tax_rate": tax_rate,
                "tax_amount": item_tax,
                "total_amount": item_amount if is_inclusive else item_amount + item_tax
            })
        
        # Calculate total amount for the template
        total_amount = subtotal if is_inclusive else subtotal + total_tax_amount
        
        templates.append({
            "id": row[0],
            "user_id": row[1],
            "customer_id": row[2],
            "template_name": row[3],
            "invoice_date_day": row[4],
            "due_date_days": row[5],
            "frequency": row[6],
            "status": row[7],
            "customer_name": row[8],
            "created_at": row[9].isoformat() if row[9] else None,
            "updated_at": row[10].isoformat() if row[10] else None,
            "tax_type": tax_type,
            "items": items,
            "totalAmount": total_amount
        })
    
    conn.close()
    return jsonify(templates), 200

@app.route('/api/recurring-templates/<int:template_id>', methods=['GET'])
@login_required
def get_recurring_template(template_id):
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_recurring_tables(cursor)
    conn.commit()
    
    cursor.execute("""
        SELECT 
            rt.id, rt.user_id, rt.customer_id, rt.template_name,
            rt.invoice_date_day, rt.due_date_days, rt.frequency, rt.status,
            c.customer_name, c.email as customer_email, c.phone as customer_phone,
            rt.tax_type
        FROM recurring_invoice_templates rt
        JOIN customers c ON rt.customer_id = c.id
        WHERE rt.id = ? AND rt.user_id = ?
    """, (template_id, current_user.id))
    
    row = cursor.fetchone()
    if not row:
        conn.close()
        return jsonify({"error": "Template not found"}), 404
    
    template = {
        "id": row[0],
        "user_id": row[1],
        "customer_id": row[2],
        "template_name": row[3],
        "invoice_date_day": row[4],
        "due_date_days": row[5],
        "frequency": row[6],
        "status": row[7],
        "customer_name": row[8],
        "customer_email": row[9],
        "customer_phone": row[10],
        "tax_type": row[11] or "inclusive",
        "items": []
    }
    
    cursor.execute("""
        SELECT id, item_name, description, quantity, unit_price,
               discount_amount, tax_rate, sort_order
        FROM recurring_invoice_template_items
        WHERE template_id = ?
        ORDER BY sort_order, id
    """, (template_id,))
    
    for item_row in cursor.fetchall():
        template["items"].append({
            "id": item_row[0],
            "item_name": item_row[1],
            "description": item_row[2],
            "quantity": float(item_row[3]) if item_row[3] else 0,
            "unit_price": float(item_row[4]) if item_row[4] else 0,
            "discount_amount": float(item_row[5]) if item_row[5] else 0,
            "tax_rate": float(item_row[6]) if item_row[6] else 0,
            "sort_order": item_row[7] or 0
        })
    
    conn.close()
    return jsonify(template), 200

@app.route('/api/recurring-templates', methods=['POST'])
@login_required
def create_recurring_template():
    data = request.get_json()
    
    required_fields = ['customer_id', 'template_name', 'invoice_date_day', 'due_date_days', 'frequency', 'items']
    for field in required_fields:
        if field not in data:
            return jsonify({"error": f"{field} is required"}), 400
    
    if not data['items'] or len(data['items']) == 0:
        return jsonify({"error": "At least one item is required"}), 400
    
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_recurring_tables(cursor)
    conn.commit()
    
    try:
        cursor.execute("""
            INSERT INTO recurring_invoice_templates 
            (user_id, customer_id, template_name, invoice_date_day, due_date_days, frequency, status, tax_type, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, 'active', ?, GETDATE(), GETDATE())
        """, (current_user.id, data['customer_id'], data['template_name'], 
              data['invoice_date_day'], data['due_date_days'], data['frequency'],
              data.get('tax_type', 'inclusive')))
        
        template_id = cursor.execute("SELECT @@IDENTITY").fetchone()[0]
        
        for index, item in enumerate(data['items']):
            cursor.execute("""
                INSERT INTO recurring_invoice_template_items 
                (template_id, item_name, description, quantity, unit_price, discount_amount, tax_rate, sort_order)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, (template_id, item['item_name'], item.get('description', ''),
                  item.get('quantity', 1), item.get('unit_price', 0),
                  item.get('discount_amount', 0), item.get('tax_rate', 0), index))
        
        conn.commit()
        
        return jsonify({"success": True, "template_id": template_id}), 201
        
    except Exception as e:
        conn.rollback()
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()

@app.route('/api/recurring-templates/<int:template_id>', methods=['PUT'])
@login_required
def update_recurring_template(template_id):
    data = request.get_json()
    
    conn = get_db_connection()
    cursor = conn.cursor()
    
    cursor.execute("SELECT id, customer_id FROM recurring_invoice_templates WHERE id = ? AND user_id = ?", 
                   (template_id, current_user.id))
    existing_template = cursor.fetchone()
    if not existing_template:
        conn.close()
        return jsonify({"error": "Template not found"}), 404
    
    try:
        cursor.execute("""
            UPDATE recurring_invoice_templates
            SET customer_id = ?,
                template_name = ?,
                invoice_date_day = ?,
                due_date_days = ?,
                frequency = ?,
                status = ?,
                tax_type = ?,
                updated_at = GETDATE()
            WHERE id = ?
        """, (data.get('customer_id', existing_template[1]), data.get('template_name'), data.get('invoice_date_day'),
              data.get('due_date_days'), data.get('frequency'),
              data.get('status', 'active'), data.get('tax_type', 'inclusive'), template_id))
        
        cursor.execute("DELETE FROM recurring_invoice_template_items WHERE template_id = ?", (template_id,))
        
        for index, item in enumerate(data.get('items', [])):
            cursor.execute("""
                INSERT INTO recurring_invoice_template_items 
                (template_id, item_name, description, quantity, unit_price, discount_amount, tax_rate, sort_order)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, (template_id, item['item_name'], item.get('description', ''),
                  item.get('quantity', 1), item.get('unit_price', 0),
                  item.get('discount_amount', 0), item.get('tax_rate', 0), index))
        
        conn.commit()
        
        return jsonify({"success": True, "message": "Template updated successfully"}), 200
        
    except Exception as e:
        conn.rollback()
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()

@app.route('/api/recurring-templates/<int:template_id>/status', methods=['PUT'])
@login_required
def update_recurring_template_status(template_id):
    data = request.get_json() or {}
    status = (data.get('status') or '').lower()
    if status not in ['active', 'inactive']:
        return jsonify({"error": "status must be active or inactive"}), 400

    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_recurring_tables(cursor)
    conn.commit()

    cursor.execute("""
        UPDATE recurring_invoice_templates
        SET status = ?, updated_at = GETDATE()
        WHERE id = ? AND user_id = ?
    """, (status, template_id, current_user.id))
    updated = cursor.rowcount
    conn.commit()
    conn.close()

    if updated == 0:
        return jsonify({"error": "Template not found"}), 404

    return jsonify({"success": True, "status": status}), 200

@app.route('/api/recurring-templates/<int:template_id>', methods=['DELETE'])
@login_required
def delete_recurring_template(template_id):
    conn = get_db_connection()
    cursor = conn.cursor()
    
    cursor.execute("DELETE FROM recurring_invoice_templates WHERE id = ? AND user_id = ?", 
                   (template_id, current_user.id))
    
    deleted = cursor.rowcount
    conn.commit()
    conn.close()
    
    if deleted == 0:
        return jsonify({"error": "Template not found"}), 404
    
    return jsonify({"success": True, "message": "Template deleted successfully"}), 200

@app.route('/api/recurring-templates/<int:template_id>/generate-invoice', methods=['POST'])
@login_required
def generate_invoice_from_template(template_id):
    conn = get_db_connection()
    cursor = conn.cursor()
    
    cursor.execute("""
        SELECT customer_id, template_name, invoice_date_day, due_date_days, tax_type
        FROM recurring_invoice_templates
        WHERE id = ? AND user_id = ?
    """, (template_id, current_user.id))
    
    template = cursor.fetchone()
    if not template:
        conn.close()
        return jsonify({"error": "Template not found"}), 404
    
    customer_id, template_name, invoice_date_day, due_date_days, tax_type = template
    
    cursor.execute("""
        SELECT item_name, description, quantity, unit_price, discount_amount, tax_rate
        FROM recurring_invoice_template_items
        WHERE template_id = ?
        ORDER BY sort_order, id
    """, (template_id,))
    
    items = []
    for row in cursor.fetchall():
        items.append({
            "item_name": row[0],
            "description": row[1] or "",
            "quantity": float(row[2]) if row[2] else 1,
            "unit_price": float(row[3]) if row[3] else 0,
            "discount_amount": float(row[4]) if row[4] else 0,
            "tax_rate": float(row[5]) if row[5] else 0
        })
    
    conn.close()
    
    today = datetime.now().date()
    invoice_date = today.replace(day=min(invoice_date_day, 28))
    if invoice_date < today:
        if invoice_date.month == 12:
            invoice_date = invoice_date.replace(year=invoice_date.year + 1, month=1)
        else:
            invoice_date = invoice_date.replace(month=invoice_date.month + 1)
    
    due_date = invoice_date + timedelta(days=due_date_days)
    
    invoice_request = {
        "customer_id": customer_id,
        "invoice_date": invoice_date.isoformat(),
        "due_date": due_date.isoformat(),
        "items": items,
        "notes": f"Generated from recurring template: {template_name}",
        "status": "pending",
        "tax_type": tax_type or "inclusive",
        "recurring_template_id": template_id,
        "is_generated_from_recurring": True
    }
    
    response, status_code = create_invoice_internal(invoice_request, 'pending')
    payload = response.get_json(silent=True) or {}

    if status_code in (200, 201) and payload.get("invoice_id"):
        invoice_id = payload["invoice_id"]
        invoice_number = payload.get("invoice_number", f"INV-{invoice_id}")

        if task_manager:
            try:
                task_manager.add_task(invoice_id, current_user.id, invoice_number)
                print(f"Generated invoice {invoice_number} enqueued for email")
            except Exception as e:
                print(f"Failed to enqueue email for generated invoice {invoice_number}: {e}")

    return response, status_code

def create_invoice_internal(data, status='pending'):
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_customer_company_columns(cursor)
    ensure_company_settings_columns(cursor)
    conn.commit()
    
    if status == 'quotation' or status == 'quote':
        cursor.execute("""
            SELECT ISNULL(MAX(CAST(REPLACE(invoice_number, 'QTN-', '') AS INT)), 0)
            FROM invoices 
            WHERE user_id = ? 
            AND invoice_number LIKE 'QTN-%'
        """, (current_user.id,))
        max_num = cursor.fetchone()[0] or 0
        next_num = max_num + 1
        invoice_number = f"QTN-{next_num:06d}"
    else:
        cursor.execute("""
            SELECT ISNULL(MAX(CAST(REPLACE(invoice_number, 'INV-', '') AS INT)), 0)
            FROM invoices 
            WHERE user_id = ? 
            AND invoice_number LIKE 'INV-%'
        """, (current_user.id,))
        max_num = cursor.fetchone()[0] or 0
        next_num = max_num + 1
        invoice_number = f"INV-{next_num:06d}"
    
    subtotal = 0
    total_tax_amount = 0
    items_data = []
    tax_type = data.get('tax_type', 'inclusive')
    is_inclusive = tax_type == 'inclusive'

    for item in data['items']:
        quantity = float(item.get('quantity', 1))
        unit_price = float(item.get('unit_price', 0))
        discount = float(item.get('discount_amount', 0))
        item_tax_rate = float(item.get('tax_rate', 0))

        item_amount = (quantity * unit_price) - discount

        item_tax = 0
        if is_inclusive:
            item_tax = item_amount - (item_amount / (1 + (item_tax_rate / 100)))
            subtotal += (item_amount - item_tax)
        else:
            item_tax = item_amount * (item_tax_rate / 100)
            subtotal += item_amount

        total_tax_amount += item_tax
        
        items_data.append({
            'item_name': item['item_name'],
            'description': item.get('description', ''),
            'quantity': quantity,
            'unit_price': unit_price,
            'discount_amount': discount,
            'tax_rate': item_tax_rate,
            'tax_amount': item_tax,
            'total_amount': item_amount if is_inclusive else item_amount + item_tax
        })
    
    tax_rate = float(data.get('tax_rate', 0))
    discount_amount = float(data.get('discount_amount', 0))
    shipping_fee = float(data.get('shipping_fee', 0))

    gross_total = subtotal + total_tax_amount
    total_amount = gross_total - discount_amount + shipping_fee

    invoice_status = data.get('status') or status
    invoice_status = invoice_status.lower() if invoice_status else 'pending'
    balance_due = total_amount if invoice_status in ['quotation', 'quote'] else total_amount
    
    due_date = data.get('due_date') if invoice_status not in ['quotation', 'quote'] else None

    # Get recurring template ID if this invoice is created from a template
    recurring_template_id = data.get('recurring_template_id')
    is_generated_from_recurring = 1 if data.get('is_generated_from_recurring') else 0
    
    # Check if we should create a recurring template from this invoice
    should_create_recurring_template = data.get('should_create_recurring_template', False)

    try:
        # Insert invoice WITHOUT any recurring columns (just tracking columns)
        cursor.execute("""
            INSERT INTO invoices (
                user_id, customer_id, invoice_number, invoice_date, due_date,
                po_number, subtotal, tax_rate, tax_amount, discount_amount, 
                shipping_fee, total_amount, amount_paid, balance_due, notes, status,
                payment_terms, terms_conditions, footer_text, tax_type,
                recurring_template_id, is_generated_from_recurring, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, GETDATE(), GETDATE())
        """, (
            current_user.id,
            data['customer_id'],
            invoice_number,
            data['invoice_date'],
            due_date,
            data.get('po_number', ''),
            subtotal,
            tax_rate,
            total_tax_amount,
            discount_amount,
            shipping_fee,
            total_amount,
            balance_due,
            data.get('notes', ''),
            invoice_status,
            data.get('payment_terms', ''),
            data.get('terms_conditions', ''),
            data.get('footer_text', ''),
            tax_type,
            recurring_template_id,
            is_generated_from_recurring
        ))
        
        invoice_id = cursor.execute("SELECT @@IDENTITY").fetchone()[0]
        
        for item in items_data:
            cursor.execute("""
                INSERT INTO invoice_items (
                    invoice_id, item_name, description, quantity, unit_price,
                    discount_amount, tax_rate, tax_amount, total_amount
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                invoice_id,
                item['item_name'],
                item['description'],
                item['quantity'],
                item['unit_price'],
                item['discount_amount'],
                item['tax_rate'],
                item['tax_amount'],
                item['total_amount']
            ))
        
        conn.commit()
        
        # If this invoice should create a recurring template, create it in recurring_invoice_templates
        if should_create_recurring_template and not is_generated_from_recurring:
            print(f"DEBUG: Creating recurring template for invoice {invoice_id}")
            create_recurring_template_from_invoice(invoice_id, data, current_user.id)
        
        return jsonify({
            "success": True,
            "invoice_id": invoice_id,
            "invoice_number": invoice_number,
            "message": f"{'Quotation' if invoice_status in ['quotation', 'quote'] else 'Invoice'} created successfully"
        }), 201
        
    except Exception as e:
        conn.rollback()
        print(f"ERROR: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()

def create_recurring_template_from_invoice(invoice_id, invoice_data, user_id):
    """Create a recurring template based on an invoice"""
    print(f"DEBUG: Creating recurring template from invoice {invoice_id}")
    
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_recurring_tables(cursor)
    conn.commit()
    
    try:
        # Get invoice details
        cursor.execute("""
            SELECT i.customer_id, i.invoice_date, i.due_date, i.tax_type,
                   c.customer_name, c.email, c.phone
            FROM invoices i
            JOIN customers c ON i.customer_id = c.id
            WHERE i.id = ? AND i.user_id = ?
        """, (invoice_id, user_id))
        
        invoice_row = cursor.fetchone()
        if not invoice_row:
            print(f"DEBUG: Invoice {invoice_id} not found")
            conn.close()
            return
        
        customer_id = invoice_row[0]
        invoice_date = invoice_row[1]
        due_date = invoice_row[2]
        tax_type = invoice_row[3] or "inclusive"
        customer_name = invoice_row[4]
        
        # Get items from the invoice
        cursor.execute("""
            SELECT item_name, description, quantity, unit_price, discount_amount, tax_rate
            FROM invoice_items
            WHERE invoice_id = ?
            ORDER BY id
        """, (invoice_id,))
        
        items = []
        for row in cursor.fetchall():
            items.append({
                "item_name": row[0],
                "description": row[1] or "",
                "quantity": float(row[2]) if row[2] else 1,
                "unit_price": float(row[3]) if row[3] else 0,
                "discount_amount": float(row[4]) if row[4] else 0,
                "tax_rate": float(row[5]) if row[5] else 0
            })
        
        # Calculate due_date_days (days between invoice_date and due_date)
        due_date_days = 30  # Default to net-30
        if invoice_date and due_date:
            try:
                if hasattr(invoice_date, 'date'):
                    inv_date = invoice_date.date()
                else:
                    inv_date = invoice_date
                if hasattr(due_date, 'date'):
                    due_date_obj = due_date.date()
                else:
                    due_date_obj = due_date
                due_date_days = (due_date_obj - inv_date).days
                due_date_days = max(due_date_days, 1)
                print(f"DEBUG: Calculated due_date_days = {due_date_days}")
            except Exception as e:
                print(f"DEBUG: Could not calculate due_date_days, using default 30. Error: {e}")
        
        # Get day of month from invoice_date (for monthly recurring)
        invoice_date_day = 1
        if invoice_date:
            if hasattr(invoice_date, 'date'):
                inv_date = invoice_date.date()
            else:
                inv_date = invoice_date
            invoice_date_day = min(inv_date.day, 28)
        
        # Get recurring settings from invoice_data
        frequency = invoice_data.get('recurring_frequency', 'monthly')
        
        # Override day if provided
        if invoice_data.get('recurring_day'):
            invoice_date_day = invoice_data.get('recurring_day')
        
        # Create descriptive template name
        template_name = f"Recurring: {customer_name}"
        
        print(f"DEBUG: Creating template with:")
        print(f"  user_id: {user_id}")
        print(f"  customer_id: {customer_id}")
        print(f"  template_name: {template_name}")
        print(f"  invoice_date_day: {invoice_date_day}")
        print(f"  due_date_days: {due_date_days}")
        print(f"  frequency: {frequency}")
        print(f"  tax_type: {tax_type}")
        print(f"  items count: {len(items)}")
        
        # Create the recurring template
        cursor.execute("""
            INSERT INTO recurring_invoice_templates 
            (user_id, customer_id, template_name, invoice_date_day, due_date_days, 
             frequency, status, tax_type, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, 'active', ?, GETDATE(), GETDATE())
        """, (user_id, customer_id, template_name, invoice_date_day, due_date_days, frequency, tax_type))
        
        template_id = cursor.execute("SELECT @@IDENTITY").fetchone()[0]
        print(f"DEBUG: Created new template {template_id}")
        
        # Insert template items
        for index, item in enumerate(items):
            cursor.execute("""
                INSERT INTO recurring_invoice_template_items 
                (template_id, item_name, description, quantity, unit_price, 
                 discount_amount, tax_rate, sort_order)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, (template_id, item['item_name'], item['description'],
                  item['quantity'], item['unit_price'], item['discount_amount'],
                  item['tax_rate'], index))
        
        conn.commit()
        print(f"DEBUG: Successfully created recurring template {template_id}")
        return template_id
        
    except Exception as e:
        conn.rollback()
        print(f"ERROR creating recurring template: {e}")
        import traceback
        traceback.print_exc()
        return None
    finally:
        conn.close()

def calculate_next_template_send_date(frequency, day_of_month=None):
    """Calculate the next date when a template should generate an invoice"""
    today = datetime.now().date()
    day_of_month = min(max(day_of_month or today.day, 1), 28)
    
    if frequency == 'weekly':
        # Next Monday
        days_ahead = 0 - today.weekday()
        if days_ahead <= 0:
            days_ahead += 7
        return today + timedelta(days=days_ahead)
    
    elif frequency == 'biweekly':
        # Every other week from next Monday
        days_ahead = 0 - today.weekday()
        if days_ahead <= 0:
            days_ahead += 7
        return today + timedelta(days=days_ahead)
    
    elif frequency == 'monthly':
        # Specific day of month
        if today.day <= day_of_month:
            return today.replace(day=day_of_month)
        else:
            # Next month
            if today.month == 12:
                return today.replace(year=today.year + 1, month=1, day=day_of_month)
            else:
                return today.replace(month=today.month + 1, day=day_of_month)
    
    elif frequency == 'bimonthly':
        # Every 2 months
        month = today.month + 2
        year = today.year
        if month > 12:
            month -= 12
            year += 1
        day = min(day_of_month, month_calendar.monthrange(year, month)[1])
        return datetime(year, month, day).date()
    
    elif frequency == 'quarterly':
        # Every 3 months
        month = today.month + 3
        year = today.year
        if month > 12:
            month -= 12
            year += 1
        day = min(day_of_month, month_calendar.monthrange(year, month)[1])
        return datetime(year, month, day).date()
    
    elif frequency == 'yearly':
        return today.replace(year=today.year + 1, day=min(day_of_month, 28))
    
    return today + timedelta(days=30)

def process_due_recurring_templates():
    """Check all active templates and generate invoices if due"""
    today = datetime.now().date()
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_recurring_tables(cursor)
    conn.commit()
    
    # Get all active templates
    cursor.execute("""
        SELECT id, user_id, customer_id, template_name, invoice_date_day, 
               due_date_days, frequency, tax_type
        FROM recurring_invoice_templates
        WHERE status = 'active'
    """)
    
    templates = cursor.fetchall()
    generated_count = 0
    errors = []
    
    for template in templates:
        template_id, user_id, customer_id, template_name, invoice_date_day, due_date_days, frequency, tax_type = template
        
        # Check if invoice should be generated today
        should_generate = False
        next_send_date = None
        
        if frequency == 'monthly':
            # Check if today is the specified day of month
            if today.day == invoice_date_day:
                should_generate = True
                # Calculate next send date for logging
                if today.month == 12:
                    next_send_date = today.replace(year=today.year + 1, month=1, day=invoice_date_day)
                else:
                    next_send_date = today.replace(month=today.month + 1, day=invoice_date_day)
        
        elif frequency == 'weekly':
            # Check if today is Monday (or specific day)
            if today.weekday() == 0:  # Monday
                should_generate = True
                next_send_date = today + timedelta(days=7)
        
        elif frequency == 'biweekly':
            # Simplified: check every other week
            week_number = today.isocalendar()[1]
            if week_number % 2 == 0:
                should_generate = True
                next_send_date = today + timedelta(days=14)
        
        elif frequency == 'quarterly':
            # Check if it's the first day of a quarter
            if today.month in [1, 4, 7, 10] and today.day == invoice_date_day:
                should_generate = True
                next_send_date = today + timedelta(days=91)
        
        elif frequency == 'yearly':
            if today.month == 1 and today.day == invoice_date_day:
                should_generate = True
                next_send_date = today.replace(year=today.year + 1)
        
        if should_generate:
            # Get template items
            cursor.execute("""
                SELECT item_name, description, quantity, unit_price, discount_amount, tax_rate
                FROM recurring_invoice_template_items
                WHERE template_id = ?
                ORDER BY sort_order, id
            """, (template_id,))
            
            items = []
            for row in cursor.fetchall():
                items.append({
                    "item_name": row[0],
                    "description": row[1] or "",
                    "quantity": float(row[2]) if row[2] else 1,
                    "unit_price": float(row[3]) if row[3] else 0,
                    "discount_amount": float(row[4]) if row[4] else 0,
                    "tax_rate": float(row[5]) if row[5] else 0
                })
            
            # Calculate due date
            due_date = today + timedelta(days=due_date_days)
            
            # Create invoice request
            invoice_request = {
                "customer_id": customer_id,
                "invoice_date": today.isoformat(),
                "due_date": due_date.isoformat(),
                "items": items,
                "notes": f"Auto-generated from recurring template: {template_name}",
                "status": "pending",
                "tax_type": tax_type or "inclusive",
                "recurring_template_id": template_id,
                "is_generated_from_recurring": True
            }
            
            # Generate invoice (using the current user context)
            # Note: This runs in background, so we need to temporarily set user context
            # You'll need to store user_id in the template and use it here
            
            try:
                # Create a temporary user context
                user_obj = User(user_id, None, None, None)
                # You need to set current_user context for this operation
                # This is complex in a background thread - consider using a task queue
                
                response, status_code = create_invoice_internal(invoice_request, 'pending')
                if status_code in (200, 201):
                    generated_count += 1
                    print(f"Generated invoice from template {template_id}")
                else:
                    errors.append({"template_id": template_id, "error": "Failed to generate"})
            except Exception as e:
                errors.append({"template_id": template_id, "error": str(e)})
    
    conn.close()
    return {"generated": generated_count, "errors": errors}

def calculate_next_monthly_date(current_date, day_of_month):
    """Calculate next month's date with the given day"""
    day_of_month = min(day_of_month, 28)
    if current_date.month == 12:
        next_month = 1
        next_year = current_date.year + 1
    else:
        next_month = current_date.month + 1
        next_year = current_date.year
    
    return datetime(next_year, next_month, day_of_month).date()

def start_recurring_template_scheduler():
    """Background thread to check templates daily"""
    def scheduler_worker():
        # Wait for app to fully start
        time.sleep(30)
        while True:
            try:
                # Run at 9 AM daily
                now = datetime.now()
                if now.hour == 9:
                    result = process_due_recurring_templates()
                    print(f"Template scheduler: generated {result['generated']} invoices")
                # Sleep for 1 hour
                time.sleep(3600)
            except Exception as e:
                print(f"Template scheduler error: {e}")
                time.sleep(3600)
    
    thread = threading.Thread(target=scheduler_worker, daemon=True)
    thread.start()

# ============================================
# CUSTOMER MANAGEMENT
# ============================================

@app.route('/api/customers', methods=['GET'])
@login_required
def get_customers():
    page = request.args.get('page', 1, type=int)
    per_page = request.args.get('per_page', 20, type=int)
    search = request.args.get('search', '')
    
    offset = (page - 1) * per_page
    
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_customer_company_columns(cursor)
    conn.commit()
    
    query = """
        SELECT id, customer_name, email, phone, address, city, state,
               postal_code, country, tax_exempt, is_company,
               company_registration_number, vat_registered, vat_number,
               company_address, notes, created_at, updated_at
        FROM customers 
        WHERE user_id = ?
    """
    params = [current_user.id]
    
    if search:
        query += """ AND (
            customer_name LIKE ? OR email LIKE ? OR phone LIKE ? OR
            company_registration_number LIKE ? OR vat_number LIKE ?
        )"""
        search_pattern = f"%{search}%"
        params.extend([search_pattern, search_pattern, search_pattern, search_pattern, search_pattern])
    
    count_query = query.replace(
        "SELECT id, customer_name, email, phone, address, city, state,\n               postal_code, country, tax_exempt, is_company,\n               company_registration_number, vat_registered, vat_number,\n               company_address, notes, created_at, updated_at",
        "SELECT COUNT(*)"
    )
    cursor.execute(count_query, params)
    count_result = cursor.fetchone()
    total = count_result[0] if count_result and count_result[0] is not None else 0
    
    if total == 0:
        conn.close()
        return jsonify({
            "customers": [],
            "total": 0,
            "page": page,
            "per_page": per_page,
            "total_pages": 0
        }), 200
    
    query += " ORDER BY customer_name OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"
    params.extend([offset, per_page])
    
    cursor.execute(query, params)
    
    customers = []
    for row in cursor.fetchall():
        customers.append({
            "id": row[0],
            "customer_name": row[1],
            "email": row[2],
            "phone": row[3],
            "address": row[4],
            "city": row[5],
            "state": row[6],
            "postal_code": row[7],
            "country": row[8],
            "tax_exempt": bool(row[9]),
            "is_company": bool(row[10]),
            "company_registration_number": row[11],
            "vat_registered": bool(row[12]),
            "vat_number": row[13],
            "company_address": row[14],
            "notes": row[15],
            "created_at": row[16].isoformat() if row[16] else None,
            "updated_at": row[17].isoformat() if row[17] else None
        })
    
    conn.close()
    
    return jsonify({
        "customers": customers,
        "total": total,
        "page": page,
        "per_page": per_page,
        "total_pages": (total + per_page - 1) // per_page
    }), 200

@app.route('/api/customers/<int:customer_id>', methods=['GET'])
@login_required
def get_customer(customer_id):
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_customer_company_columns(cursor)
    conn.commit()

    cursor.execute("""
        SELECT id, customer_name, email, phone, address, city, state,
               postal_code, country, tax_exempt, is_company,
               company_registration_number, vat_registered, vat_number,
               company_address, notes, created_at, updated_at
        FROM customers
        WHERE id = ? AND user_id = ?
    """, (customer_id, current_user.id))

    row = cursor.fetchone()
    conn.close()

    if not row:
        return jsonify({"error": "Customer not found"}), 404

    return jsonify({
        "id": row[0],
        "customer_name": row[1],
        "email": row[2],
        "phone": row[3],
        "address": row[4],
        "city": row[5],
        "state": row[6],
        "postal_code": row[7],
        "country": row[8],
        "tax_exempt": bool(row[9]),
        "is_company": bool(row[10]),
        "company_registration_number": row[11],
        "vat_registered": bool(row[12]),
        "vat_number": row[13],
        "company_address": row[14],
        "notes": row[15],
        "created_at": row[16].isoformat() if row[16] else None,
        "updated_at": row[17].isoformat() if row[17] else None
    }), 200

@app.route('/api/customers', methods=['POST'])
@login_required
def create_customer():
    data = request.get_json()
    
    required_fields = ['customer_name', 'email']
    for field in required_fields:
        if not data.get(field):
            return jsonify({"error": f"{field} is required"}), 400
    
    conn = get_db_connection()
    cursor = conn.cursor()
    
    try:
        ensure_customer_company_columns(cursor)
        cursor.execute("""
            INSERT INTO customers (user_id, customer_name, email, phone, address, 
                                 city, state, postal_code, country, tax_exempt,
                                 is_company, company_registration_number,
                                 vat_registered, vat_number, company_address, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            current_user.id,
            data['customer_name'],
            data['email'],
            data.get('phone', ''),
            data.get('address', ''),
            data.get('city', ''),
            data.get('state', ''),
            data.get('postal_code', ''),
            data.get('country', 'USA'),
            1 if data.get('tax_exempt') else 0,
            1 if data.get('is_company') else 0,
            data.get('company_registration_number', ''),
            1 if data.get('vat_registered') else 0,
            data.get('vat_number', ''),
            data.get('company_address', ''),
            data.get('notes', '')
        ))
        
        conn.commit()
        customer_id = cursor.execute("SELECT @@IDENTITY").fetchone()[0]
        
        return jsonify({
            "success": True,
            "customer_id": customer_id,
            "message": "Customer created successfully"
        }), 201
        
    except Exception as e:
        conn.rollback()
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()

@app.route('/api/customers/<int:customer_id>', methods=['PUT'])
@login_required
def update_customer(customer_id):
    data = request.get_json()
    
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_customer_company_columns(cursor)
    conn.commit()
    
    cursor.execute("SELECT id FROM customers WHERE id = ? AND user_id = ?", 
                   (customer_id, current_user.id))
    if not cursor.fetchone():
        conn.close()
        return jsonify({"error": "Customer not found"}), 404
    
    try:
        cursor.execute("""
            UPDATE customers 
            SET customer_name = ?, email = ?, phone = ?, address = ?,
                city = ?, state = ?, postal_code = ?, country = ?,
                tax_exempt = ?, is_company = ?, company_registration_number = ?,
                vat_registered = ?, vat_number = ?, company_address = ?,
                notes = ?, updated_at = GETDATE()
            WHERE id = ? AND user_id = ?
        """, (
            data.get('customer_name'),
            data.get('email'),
            data.get('phone', ''),
            data.get('address', ''),
            data.get('city', ''),
            data.get('state', ''),
            data.get('postal_code', ''),
            data.get('country', 'USA'),
            1 if data.get('tax_exempt') else 0,
            1 if data.get('is_company') else 0,
            data.get('company_registration_number', ''),
            1 if data.get('vat_registered') else 0,
            data.get('vat_number', ''),
            data.get('company_address', ''),
            data.get('notes', ''),
            customer_id,
            current_user.id
        ))
        
        conn.commit()
        
        return jsonify({
            "success": True,
            "message": "Customer updated successfully"
        }), 200
        
    except Exception as e:
        conn.rollback()
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()

@app.route('/api/customers/<int:customer_id>', methods=['DELETE'])
@login_required
def delete_customer(customer_id):
    conn = get_db_connection()
    cursor = conn.cursor()
    
    cursor.execute("SELECT COUNT(*) FROM invoices WHERE customer_id = ?", (customer_id,))
    invoice_count = cursor.fetchone()[0]
    
    if invoice_count > 0:
        conn.close()
        return jsonify({"error": f"Cannot delete customer with {invoice_count} invoices"}), 400
    
    cursor.execute("DELETE FROM customers WHERE id = ? AND user_id = ?", 
                   (customer_id, current_user.id))
    
    conn.commit()
    conn.close()
    
    return jsonify({"success": True, "message": "Customer deleted successfully"}), 200

# ============================================
# INVOICE CRUD OPERATIONS
# ============================================

@app.route('/api/invoices', methods=['GET'])
@login_required
def get_invoices():
    page = request.args.get('page', 1, type=int)
    per_page = request.args.get('per_page', 20, type=int)
    status = request.args.get('status', '')
    customer_id = request.args.get('customer_id', type=int)
    date_from = request.args.get('date_from', '')
    date_to = request.args.get('date_to', '')
    search = request.args.get('search', '')
    
    offset = (page - 1) * per_page
    today = datetime.now().date()
    
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_invoice_operational_columns(cursor)
    conn.commit()
    
    cursor.execute("""
        UPDATE invoices 
        SET status = 'overdue' 
        WHERE status = 'pending' 
        AND due_date < ? 
        AND balance_due > 0
        AND status NOT IN ('quotation', 'quote', 'paid')
    """, (today,))
    conn.commit()
    
    query = """
        SELECT i.id, i.invoice_number, i.invoice_date, i.due_date, i.status,
               i.subtotal, i.tax_amount, i.discount_amount, i.shipping_fee,
               i.total_amount, i.amount_paid, i.balance_due,
               c.customer_name, c.email as customer_email, i.tax_rate, i.tax_type,
               i.updated_at
        FROM invoices i
        JOIN customers c ON i.customer_id = c.id
        WHERE i.user_id = ?
    """
    params = [current_user.id]
    
    if status and status.strip():
        if status.lower() == 'quotation':
            query += " AND i.status IN ('quotation', 'quote')"
        else:
            query += " AND i.status = ?"
            params.append(status)
    
    if customer_id:
        query += " AND i.customer_id = ?"
        params.append(customer_id)
    
    if date_from:
        query += " AND i.invoice_date >= ?"
        params.append(date_from)
    
    if date_to:
        query += " AND i.invoice_date <= ?"
        params.append(date_to)
    
    if search and search.strip():
        query += " AND (i.invoice_number LIKE ? OR c.customer_name LIKE ?)"
        search_param = f"%{search}%"
        params.extend([search_param, search_param])
    
    count_query = query.replace(
        "SELECT i.id, i.invoice_number, i.invoice_date, i.due_date, i.status, i.subtotal, i.tax_amount, i.discount_amount, i.shipping_fee, i.total_amount, i.amount_paid, i.balance_due, c.customer_name, c.email as customer_email, i.tax_rate, i.tax_type",
        "SELECT COUNT(*)"
    )
    cursor.execute(count_query, params)
    count_result = cursor.fetchone()
    total = count_result[0] if count_result and count_result[0] is not None else 0
    
    if total == 0:
        conn.close()
        return jsonify({
            "invoices": [],
            "total": 0,
            "page": page,
            "per_page": per_page,
            "total_pages": 0
        }), 200
    
    query += " ORDER BY i.invoice_date DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"
    params.extend([offset, per_page])
    
    cursor.execute(query, params)
    
    invoices = []
    for row in cursor.fetchall():
        invoices.append({
            "id": row[0],
            "invoice_number": row[1],
            "invoice_date": row[2].isoformat() if row[2] else None,
            "due_date": row[3].isoformat() if row[3] else None,
            "status": row[4],
            "subtotal": float(row[5]) if row[5] else 0,
            "tax_amount": float(row[6]) if row[6] else 0,
            "discount_amount": float(row[7]) if row[7] else 0,
            "shipping_fee": float(row[8]) if row[8] else 0,
            "total_amount": float(row[9]) if row[9] else 0,
            "amount_paid": float(row[10]) if row[10] else 0,
            "balance_due": float(row[11]) if row[11] else 0,
            "customer_name": row[12],
            "customer_email": row[13],
            "tax_rate": float(row[14]) if row[14] else 0,
            "tax_type": row[15] or "exclusive",
            "updated_at": row[16].isoformat() if row[16] else None
        })
    
    conn.close()
    
    return jsonify({
        "invoices": invoices,
        "total": total,
        "page": page,
        "per_page": per_page,
        "total_pages": (total + per_page - 1) // per_page
    }), 200

def create_invoice_internal(data, status='pending'):
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_customer_company_columns(cursor)
    ensure_company_settings_columns(cursor)
    conn.commit()
    
    if status == 'quotation' or status == 'quote':
        cursor.execute("""
            SELECT ISNULL(MAX(CAST(REPLACE(invoice_number, 'QTN-', '') AS INT)), 0)
            FROM invoices 
            WHERE user_id = ? 
            AND invoice_number LIKE 'QTN-%'
        """, (current_user.id,))
        max_num = cursor.fetchone()[0] or 0
        next_num = max_num + 1
        invoice_number = f"QTN-{next_num:06d}"
    else:
        cursor.execute("""
            SELECT ISNULL(MAX(CAST(REPLACE(invoice_number, 'INV-', '') AS INT)), 0)
            FROM invoices 
            WHERE user_id = ? 
            AND invoice_number LIKE 'INV-%'
        """, (current_user.id,))
        max_num = cursor.fetchone()[0] or 0
        next_num = max_num + 1
        invoice_number = f"INV-{next_num:06d}"
    
    subtotal = 0
    total_tax_amount = 0
    items_data = []
    tax_type = data.get('tax_type', 'inclusive')
    is_inclusive = tax_type == 'inclusive'

    for item in data['items']:
        quantity = float(item.get('quantity', 1))
        unit_price = float(item.get('unit_price', 0))
        discount = float(item.get('discount_amount', 0))
        item_tax_rate = float(item.get('tax_rate', 0))

        item_amount = (quantity * unit_price) - discount

        item_tax = 0
        if is_inclusive:
            item_tax = item_amount - (item_amount / (1 + (item_tax_rate / 100)))
            subtotal += (item_amount - item_tax)
        else:
            item_tax = item_amount * (item_tax_rate / 100)
            subtotal += item_amount

        total_tax_amount += item_tax
        
        items_data.append({
            'item_name': item['item_name'],
            'description': item.get('description', ''),
            'quantity': quantity,
            'unit_price': unit_price,
            'discount_amount': discount,
            'tax_rate': item_tax_rate,
            'tax_amount': item_tax,
            'total_amount': item_amount if is_inclusive else item_amount + item_tax
        })
    
    tax_rate = float(data.get('tax_rate', 0))
    discount_amount = float(data.get('discount_amount', 0))
    shipping_fee = float(data.get('shipping_fee', 0))

    gross_total = subtotal + total_tax_amount
    total_amount = gross_total - discount_amount + shipping_fee

    invoice_status = data.get('status') or status
    invoice_status = invoice_status.lower() if invoice_status else 'pending'
    balance_due = total_amount if invoice_status in ['quotation', 'quote'] else total_amount
    
    due_date = data.get('due_date') if invoice_status not in ['quotation', 'quote'] else None

    # Get recurring template ID if this invoice is created from a template
    recurring_template_id = data.get('recurring_template_id')
    is_generated_from_recurring = 1 if data.get('is_generated_from_recurring') else 0
    
    # Check both possible field names from Android app
    should_create_recurring = data.get('should_create_recurring_template', False) or data.get('is_recurring', False)

    try:
        cursor.execute("""
            INSERT INTO invoices (
                user_id, customer_id, invoice_number, invoice_date, due_date,
                po_number, subtotal, tax_rate, tax_amount, discount_amount, 
                shipping_fee, total_amount, amount_paid, balance_due, notes, status,
                payment_terms, terms_conditions, footer_text, tax_type,
                recurring_template_id, is_generated_from_recurring, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, GETDATE(), GETDATE())
        """, (
            current_user.id,
            data['customer_id'],
            invoice_number,
            data['invoice_date'],
            due_date,
            data.get('po_number', ''),
            subtotal,
            tax_rate,
            total_tax_amount,
            discount_amount,
            shipping_fee,
            total_amount,
            balance_due,
            data.get('notes', ''),
            invoice_status,
            data.get('payment_terms', ''),
            data.get('terms_conditions', ''),
            data.get('footer_text', ''),
            tax_type,
            recurring_template_id,
            is_generated_from_recurring
        ))
        
        invoice_id = cursor.execute("SELECT @@IDENTITY").fetchone()[0]
        
        for item in items_data:
            cursor.execute("""
                INSERT INTO invoice_items (
                    invoice_id, item_name, description, quantity, unit_price,
                    discount_amount, tax_rate, tax_amount, total_amount
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                invoice_id,
                item['item_name'],
                item['description'],
                item['quantity'],
                item['unit_price'],
                item['discount_amount'],
                item['tax_rate'],
                item['tax_amount'],
                item['total_amount']
            ))
        
        conn.commit()
        
        # If this invoice should create a recurring template, create it
        if should_create_recurring and not is_generated_from_recurring:
            print(f"DEBUG: Creating recurring template for invoice {invoice_id}")
            create_recurring_template_from_invoice(invoice_id, data, current_user.id)
        
        return jsonify({
            "success": True,
            "invoice_id": invoice_id,
            "invoice_number": invoice_number,
            "message": f"{'Quotation' if invoice_status in ['quotation', 'quote'] else 'Invoice'} created successfully"
        }), 201
        
    except Exception as e:
        conn.rollback()
        print(f"ERROR: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()

@app.route('/api/invoices', methods=['POST'])
@login_required
def create_invoice():
    data = request.get_json()
    
    required_fields = ['customer_id', 'invoice_date', 'due_date', 'items']
    for field in required_fields:
        if field not in data:
            return jsonify({"error": f"{field} is required"}), 400
    
    if not data['items'] or len(data['items']) == 0:
        return jsonify({"error": "At least one invoice item is required"}), 400
    
    # Pass recurring data to create_invoice_internal
    # This data will be used to create a template
    return create_invoice_internal(data, 'pending')

@app.route('/api/quotations', methods=['POST'])
@login_required
def create_quotation():
    data = request.get_json()
    
    required_fields = ['customer_id', 'invoice_date', 'items']
    for field in required_fields:
        if field not in data:
            return jsonify({"error": f"{field} is required"}), 400
    
    if not data['items'] or len(data['items']) == 0:
        return jsonify({"error": "At least one item is required"}), 400
    
    data['status'] = 'quotation'
    
    return create_invoice_internal(data, 'quotation')

@app.route('/api/invoices/draft', methods=['POST'])
@login_required
def create_invoice_as_draft():
    data = request.get_json()
    
    required_fields = ['customer_id', 'invoice_date', 'due_date', 'items']
    for field in required_fields:
        if field not in data:
            return jsonify({"error": f"{field} is required"}), 400
    
    if not data['items'] or len(data['items']) == 0:
        return jsonify({"error": "At least one invoice item is required"}), 400
    
    return create_invoice_internal(data, 'draft')

def update_invoice_internal(invoice_id, data, status=None):
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_invoice_operational_columns(cursor)
    conn.commit()
    
    cursor.execute("""
        SELECT id, status, amount_paid, tax_rate, discount_amount, shipping_fee, tax_type
        FROM invoices
        WHERE id = ? AND user_id = ?
    """, (invoice_id, current_user.id))
    invoice = cursor.fetchone()
    
    if not invoice:
        conn.close()
        return jsonify({"error": "Invoice not found"}), 404
    
    if invoice[1] == 'paid':
        conn.close()
        return jsonify({"error": "Cannot update a paid invoice"}), 400
    
    try:
        update_fields = []
        params = []
        
        if 'customer_id' in data:
            update_fields.append("customer_id = ?")
            params.append(data['customer_id'])
        if 'invoice_date' in data:
            update_fields.append("invoice_date = ?")
            params.append(data['invoice_date'])
        if 'due_date' in data:
            update_fields.append("due_date = ?")
            params.append(data['due_date'])
        if 'po_number' in data:
            update_fields.append("po_number = ?")
            params.append(data.get('po_number', ''))
        if 'tax_rate' in data:
            update_fields.append("tax_rate = ?")
            params.append(data.get('tax_rate', 0))
        if 'discount_amount' in data:
            update_fields.append("discount_amount = ?")
            params.append(data.get('discount_amount', 0))
        if 'shipping_fee' in data:
            update_fields.append("shipping_fee = ?")
            params.append(data.get('shipping_fee', 0))
        if 'notes' in data:
            update_fields.append("notes = ?")
            params.append(data.get('notes', ''))
        if 'payment_terms' in data:
            update_fields.append("payment_terms = ?")
            params.append(data.get('payment_terms', ''))
        if 'terms_conditions' in data:
            update_fields.append("terms_conditions = ?")
            params.append(data.get('terms_conditions', ''))
        if 'footer_text' in data:
            update_fields.append("footer_text = ?")
            params.append(data.get('footer_text', ''))
        if 'tax_type' in data:
            update_fields.append("tax_type = ?")
            params.append(data.get('tax_type', 'exclusive'))
        
        # NO recurring column updates here - those belong to templates, not invoices
        
        target_status = status or data.get('status')
        if target_status in ['draft', 'pending', 'overdue', 'paid', 'cancelled', 'quotation', 'quote']:
            update_fields.append("status = ?")
            params.append(target_status)
        
        update_fields.append("updated_at = GETDATE()")
        params.append(invoice_id)
        
        if update_fields:
            query = f"UPDATE invoices SET {', '.join(update_fields)} WHERE id = ? AND user_id = ?"
            cursor.execute(query, params + [current_user.id])

        # Rest of the update logic remains the same...
        tax_type = data.get('tax_type', invoice[6] or 'exclusive')
        is_inclusive = tax_type == 'inclusive'

        new_subtotal = 0
        total_tax_amount = 0

        if 'items' in data and data['items']:
            cursor.execute("DELETE FROM invoice_items WHERE invoice_id = ?", (invoice_id,))
            
            for item in data['items']:
                quantity = float(item.get('quantity', 1))
                unit_price = float(item.get('unit_price', 0))
                discount = float(item.get('discount_amount', 0))
                item_tax_rate = float(item.get('tax_rate', 0))

                item_amount = (quantity * unit_price) - discount

                item_tax = 0
                if is_inclusive:
                    item_tax = item_amount - (item_amount / (1 + (item_tax_rate / 100)))
                    new_subtotal += (item_amount - item_tax)
                else:
                    item_tax = item_amount * (item_tax_rate / 100)
                    new_subtotal += item_amount

                total_tax_amount += item_tax
                
                cursor.execute("""
                    INSERT INTO invoice_items (
                        invoice_id, item_name, description, quantity, unit_price,
                        discount_amount, tax_rate, tax_amount, total_amount
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    invoice_id,
                    item['item_name'],
                    item.get('description', ''),
                    quantity,
                    unit_price,
                    discount,
                    item_tax_rate,
                    item_tax,
                    item_amount if is_inclusive else item_amount + item_tax
                ))
        else:
            cursor.execute("SELECT quantity, unit_price, discount_amount, tax_rate FROM invoice_items WHERE invoice_id = ?", (invoice_id,))
            for row in cursor.fetchall():
                q, p, d, r = float(row[0]), float(row[1]), float(row[2]), float(row[3])
                amt = (q * p) - d
                if is_inclusive:
                    tax = amt - (amt / (1 + (r / 100)))
                    new_subtotal += (amt - tax)
                    total_tax_amount += tax
                else:
                    tax = amt * (r / 100)
                    new_subtotal += amt
                    total_tax_amount += tax

        tax_rate = float(data.get('tax_rate', invoice[3] or 0))
        discount_amount = float(data.get('discount_amount', invoice[4] or 0))
        shipping_fee = float(data.get('shipping_fee', invoice[5] or 0))

        gross_total = new_subtotal + total_tax_amount
        new_total = gross_total - discount_amount + shipping_fee

        amount_paid = float(invoice[2] or 0)
        new_balance_due = max(new_total - amount_paid, 0)
        
        cursor.execute("""
            UPDATE invoices 
            SET subtotal = ?,
                tax_rate = ?,
                tax_amount = ?,
                discount_amount = ?,
                shipping_fee = ?,
                total_amount = ?,
                balance_due = ?,
                updated_at = GETDATE()
            WHERE id = ?
        """, (new_subtotal, tax_rate, total_tax_amount, discount_amount, shipping_fee, new_total, new_balance_due, invoice_id))
        
        conn.commit()
        
        return jsonify({
            "success": True,
            "message": "Invoice updated successfully"
        }), 200
        
    except Exception as e:
        conn.rollback()
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()

@app.route('/api/invoices/<int:invoice_id>', methods=['PUT'])
@login_required
def update_invoice(invoice_id):
    data = request.get_json()
    return update_invoice_internal(invoice_id, data)

@app.route('/api/invoices/<int:invoice_id>/draft', methods=['PUT'])
@login_required
def update_invoice_as_draft(invoice_id):
    data = request.get_json()
    return update_invoice_internal(invoice_id, data, 'draft')

@app.route('/api/recurring-invoices/queue-status', methods=['GET'])
@login_required
def get_recurring_invoice_queue_status():
    today = datetime.now().date()
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_invoice_operational_columns(cursor)
    conn.commit()

    cursor.execute("""
        SELECT COUNT(*)
        FROM invoices
        WHERE user_id = ?
          AND is_recurring = 1
          AND recurring_active = 1
          AND status NOT IN ('cancelled', 'quotation', 'quote')
    """, (current_user.id,))
    active_count = cursor.fetchone()[0] or 0

    cursor.execute("""
        SELECT COUNT(*)
        FROM invoices
        WHERE user_id = ?
          AND is_recurring = 1
          AND recurring_active = 1
          AND status NOT IN ('cancelled', 'quotation', 'quote')
          AND (recurring_next_send_date IS NULL OR recurring_next_send_date <= ?)
    """, (current_user.id, today))
    due_count = cursor.fetchone()[0] or 0

    cursor.execute("""
        SELECT MIN(recurring_next_send_date)
        FROM invoices
        WHERE user_id = ?
          AND is_recurring = 1
          AND recurring_active = 1
          AND recurring_next_send_date IS NOT NULL
    """, (current_user.id,))
    next_row = cursor.fetchone()
    conn.close()

    next_date = next_row[0].isoformat() if next_row and next_row[0] else None
    return jsonify({
        "active_count": active_count,
        "due_count": due_count,
        "next_send_date": next_date
    }), 200

@app.route('/api/recurring-invoices/process-due', methods=['POST'])
@login_required
def process_due_recurring_invoices():
    today = datetime.now().date()
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_invoice_operational_columns(cursor)
    conn.commit()

    cursor.execute("""
        SELECT id, customer_id, recurring_frequency, recurring_day, due_date,
               tax_rate, discount_amount, shipping_fee, notes, payment_terms,
               terms_conditions, footer_text, tax_type
        FROM invoices
        WHERE user_id = ?
          AND is_recurring = 1
          AND recurring_active = 1
          AND status NOT IN ('cancelled', 'quotation', 'quote')
          AND (recurring_next_send_date IS NULL OR recurring_next_send_date <= ?)
    """, (current_user.id, today))
    due_invoices = cursor.fetchall()

    generated = []
    errors = []

    for invoice in due_invoices:
        source_invoice_id = invoice[0]
        cursor.execute("""
            SELECT item_name, description, quantity, unit_price, discount_amount, tax_rate
            FROM invoice_items
            WHERE invoice_id = ?
            ORDER BY sort_order, id
        """, (source_invoice_id,))
        items = [{
            "item_name": row[0],
            "description": row[1] or "",
            "quantity": float(row[2]) if row[2] else 1,
            "unit_price": float(row[3]) if row[3] else 0,
            "discount_amount": float(row[4]) if row[4] else 0,
            "tax_rate": float(row[5]) if row[5] else 0
        } for row in cursor.fetchall()]

        if not items:
            errors.append({"invoice_id": source_invoice_id, "error": "No invoice items found"})
            continue

        next_invoice_date = today
        original_due_date = invoice[4]
        due_days = 30
        if original_due_date:
            try:
                due_days = max((original_due_date - today).days, 0)
            except Exception:
                due_days = 30

        invoice_request = {
            "customer_id": invoice[1],
            "invoice_date": next_invoice_date.isoformat(),
            "due_date": (next_invoice_date + timedelta(days=due_days)).isoformat(),
            "tax_rate": float(invoice[5]) if invoice[5] else 0,
            "discount_amount": float(invoice[6]) if invoice[6] else 0,
            "shipping_fee": float(invoice[7]) if invoice[7] else 0,
            "notes": invoice[8] or "",
            "payment_terms": invoice[9] or "",
            "terms_conditions": invoice[10] or "",
            "footer_text": invoice[11] or "",
            "tax_type": invoice[12] or "inclusive",
            "items": items,
            "status": "pending",
            "is_generated_from_recurring": True
        }

        response, status_code = create_invoice_internal(invoice_request, 'pending')
        payload = response.get_json(silent=True) or {}
        if status_code in (200, 201) and payload.get("invoice_id"):
            invoice_id = payload["invoice_id"]
            invoice_number = payload.get("invoice_number", f"INV-{invoice_id}")
            generated.append(payload)

            if task_manager:
                try:
                    task_manager.add_task(invoice_id, current_user.id, invoice_number)
                    print(f"Recurring invoice {invoice_number} enqueued for email")
                except Exception as e:
                    print(f"Failed to enqueue email for recurring invoice {invoice_number}: {e}")

            new_next_send_date = calculate_recurring_next_send_date(invoice[2], invoice[3]).isoformat()
            cursor.execute("""
                UPDATE invoices
                SET recurring_next_send_date = ?, updated_at = GETDATE()
                WHERE id = ? AND user_id = ?
            """, (new_next_send_date, source_invoice_id, current_user.id))
            conn.commit()
        else:
            errors.append({"invoice_id": source_invoice_id, "error": payload.get("error", "Failed to generate invoice")})

    conn.close()
    return jsonify({
        "success": len(errors) == 0,
        "generated_count": len(generated),
        "generated": generated,
        "errors": errors
    }), 200 if not errors else 207

def get_invoice_email_payload(invoice_id, user_id=None):
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_company_settings_columns(cursor)
    conn.commit()
    owner_id = user_id if user_id is not None else current_user.id

    cursor.execute("""
        SELECT 
            i.*,
            c.customer_name, c.email as customer_email, c.phone as customer_phone,
            c.address as customer_address, c.city as customer_city, c.state as customer_state,
            c.postal_code as customer_postal_code, c.country as customer_country,
            c.is_company as customer_is_company,
            c.company_registration_number as customer_company_registration_number,
            c.vat_registered as customer_vat_registered,
            c.vat_number as customer_vat_number,
            c.company_address as customer_company_address,
            cs.company_name, cs.company_logo_url, cs.company_address, cs.company_phone,
            cs.company_email, cs.company_website, cs.currency_symbol, cs.currency_code,
            cs.tax_rate_default, cs.invoice_notes_default, cs.terms_conditions_default,
            cs.footer_text_default, cs.company_vat_number, cs.bank_account_details
        FROM invoices i
        JOIN customers c ON i.customer_id = c.id
        LEFT JOIN company_settings cs ON cs.user_id = i.user_id
        WHERE i.id = ? AND i.user_id = ?
    """, (invoice_id, owner_id))

    invoice_row = cursor.fetchone()
    if not invoice_row:
        conn.close()
        return None, None

    columns = [column[0] for column in cursor.description]
    invoice = {}
    for i, col in enumerate(columns):
        value = invoice_row[i]
        if isinstance(value, decimal.Decimal):
            value = float(value)
        elif hasattr(value, 'isoformat'):
            value = value.isoformat() if value else None
        invoice[col] = value

    cursor.execute("""
        SELECT id, item_name, description, quantity, unit_price,
               discount_amount, tax_rate, tax_amount, total_amount
        FROM invoice_items
        WHERE invoice_id = ?
        ORDER BY id
    """, (invoice_id,))

    items = []
    for row in cursor.fetchall():
        items.append({
            "id": row[0],
            "item_name": row[1],
            "description": row[2],
            "quantity": float(row[3]) if row[3] else 0,
            "unit_price": float(row[4]) if row[4] else 0,
            "discount_amount": float(row[5]) if row[5] else 0,
            "tax_rate": float(row[6]) if row[6] else 0,
            "tax_amount": float(row[7]) if row[7] else 0,
            "total_amount": float(row[8]) if row[8] else 0
        })

    conn.close()
    return invoice, items

def create_invoice_pdf_html(invoice, items):
    invoice['invoice_date_formatted'] = format_date(invoice.get('invoice_date', ''))
    invoice['due_date_formatted'] = format_date(invoice.get('due_date', ''))
    company_name = invoice.get('company_name') or 'InvoiceApp'

    def format_currency(amount):
        if amount is None:
            return "R0.00"
        return f"R{float(amount):,.2f}"

    return render_template(
        'pdf_invoice.html',
        invoice=invoice,
        items=items,
        company_name=company_name,
        format_currency=format_currency
    )

def send_invoice_email_helper(invoice_id, user_id=None, tracking_url=None):
    if not EMAIL_ADDRESS or not EMAIL_PASSWORD:
        raise Exception("Email settings are missing. Configure EMAIL_ADDRESS and EMAIL_PASSWORD in Flask/.env.")

    invoice, items = get_invoice_email_payload(invoice_id, user_id)
    if not invoice:
        raise Exception("Invoice not found")

    customer_email = invoice.get('customer_email')
    if not customer_email:
        raise Exception("Customer does not have an email address")

    company_name = invoice.get('company_name') or 'InvoiceApp'
    invoice_number = invoice.get('invoice_number', '')
    subject = f"Invoice {invoice_number} from {company_name}"
    html_content = create_invoice_pdf_html(invoice, items)
    if tracking_url:
        html_content = html_content.replace(
            "</body>",
            f'<img src="{tracking_url}" width="1" height="1" alt="" style="display:none;width:1px;height:1px;border:0;" /></body>'
        )

    msg = MIMEMultipart('mixed')
    msg["From"] = EMAIL_ADDRESS
    msg["To"] = customer_email
    msg["Subject"] = subject

    body = MIMEMultipart('alternative')
    body.attach(MIMEText(html_content, "html"))
    msg.attach(body)

    try:
        pdf_data = pdfkit.from_string(html_content, False, configuration=pdfkit_config, options={
            'page-size': 'A4',
            'margin-top': '0.75in',
            'margin-right': '0.75in',
            'margin-bottom': '0.75in',
            'margin-left': '0.75in',
            'encoding': "UTF-8",
            'no-outline': None,
            'enable-local-file-access': None
        })
        attachment = MIMEApplication(pdf_data, _subtype="pdf")
        attachment.add_header('Content-Disposition', 'attachment', filename=f'invoice_{invoice_number}.pdf')
        msg.attach(attachment)
    except Exception as e:
        print(f"PDF attachment skipped: {e}")

    with smtplib.SMTP(SMTP_SERVER, SMTP_PORT) as server:
        server.starttls()
        server.login(EMAIL_ADDRESS, EMAIL_PASSWORD)
        server.sendmail(EMAIL_ADDRESS, customer_email, msg.as_string())

    return customer_email

def record_invoice_sent_communication(invoice_id, user_id, sent_to):
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("""
        SELECT
            i.invoice_number,
            i.customer_id,
            i.total_amount,
            c.customer_name
        FROM invoices i
        JOIN customers c ON i.customer_id = c.id
        WHERE i.id = ? AND i.user_id = ?
    """, (invoice_id, user_id))
    invoice = cursor.fetchone()

    if invoice:
        invoice_number, customer_id, total_amount, customer_name = invoice
        subject = f"Invoice {invoice_number}"
        sent_to_text = f" at {sent_to}" if sent_to else ""
        message = f"Invoice {invoice_number} was emailed to {customer_name}{sent_to_text}. Amount: R{float(total_amount or 0):,.2f}"
        cursor.execute("""
            INSERT INTO invoice_communications
            (invoice_id, customer_id, communication_type, subject, message_body, sent_at, created_by)
            OUTPUT INSERTED.id
            VALUES (?, ?, 'sent', ?, ?, GETDATE(), ?)
        """, (invoice_id, customer_id, subject, message, user_id))
        communication_id = cursor.fetchone()[0]
        conn.commit()
    else:
        communication_id = None

    conn.close()
    return communication_id

@app.route('/api/invoices/<int:invoice_id>/send', methods=['POST'])
@login_required
def send_invoice(invoice_id):
    data = request.get_json(silent=True) or {}

    if data:
        update_result = update_invoice_internal(invoice_id, data, 'pending')
        status_code = update_result[1] if isinstance(update_result, tuple) and len(update_result) > 1 else 200
        if status_code >= 400:
            return update_result
    else:
        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute("""
            UPDATE invoices
            SET status = CASE WHEN status = 'draft' THEN 'pending' ELSE status END,
                updated_at = GETDATE()
            WHERE id = ? AND user_id = ?
        """, (invoice_id, current_user.id))
        if cursor.rowcount == 0:
            conn.rollback()
            conn.close()
            return jsonify({"error": "Invoice not found"}), 404
        conn.commit()
        conn.close()

    try:
        communication_id = record_invoice_sent_communication(invoice_id, current_user.id, "")
        tracking_url = url_for(
            'track_communication_open',
            communication_id=communication_id,
            _external=True
        ) if communication_id else None
        sent_to = send_invoice_email_helper(invoice_id, tracking_url=tracking_url)
        return jsonify({
            "success": True,
            "message": "Invoice sent successfully",
            "sent_to": sent_to
        }), 200
    except Exception as e:
        if 'communication_id' in locals() and communication_id:
            try:
                cleanup_conn = get_db_connection()
                cleanup_cursor = cleanup_conn.cursor()
                cleanup_cursor.execute("DELETE FROM invoice_communications WHERE id = ?", (communication_id,))
                cleanup_conn.commit()
                cleanup_conn.close()
            except Exception as cleanup_error:
                print(f"Error cleaning failed communication record: {cleanup_error}")
        return jsonify({
            "success": False,
            "error": str(e),
            "message": "Invoice was saved as pending, but email sending failed"
        }), 500

@app.route('/api/payments/<int:payment_id>/reverse', methods=['POST'])
@login_required
def reverse_payment(payment_id):
    conn = None
    cursor = None
    
    try:
        data = request.get_json() or {}
        reversal_reason = data.get('reason', 'Payment reversal')
        
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute("""
            SELECT p.id, p.invoice_id, p.amount, p.status,
                   i.id, i.total_amount, i.amount_paid, i.balance_due, i.status,
                   i.user_id
            FROM payments p
            JOIN invoices i ON p.invoice_id = i.id
            WHERE p.id = ? AND p.user_id = ?
        """, (payment_id, current_user.id))
        
        payment = cursor.fetchone()
        
        if not payment:
            return jsonify({"error": "Payment not found"}), 404
        
        (payment_id_db, invoice_id, amount, payment_status, 
         invoice_id_db, total_amount, amount_paid, balance_due, invoice_status, invoice_user_id) = payment
        
        if payment_status == 'reversed':
            return jsonify({"error": "Payment already reversed"}), 400
        
        new_amount_paid = amount_paid - amount
        new_balance_due = total_amount - new_amount_paid
        new_invoice_status = 'paid' if new_balance_due <= 0 else ('pending' if invoice_status != 'draft' else 'draft')
        
        cursor.execute("""
            UPDATE payments 
            SET status = 'reversed',
                reversed_at = GETDATE(),
                reversed_by = ?,
                reversal_reason = ?
            WHERE id = ? AND user_id = ?
        """, (current_user.id, reversal_reason, payment_id, current_user.id))
        
        cursor.execute("""
            UPDATE invoices 
            SET amount_paid = ?,
                balance_due = ?,
                status = ?,
                updated_at = GETDATE()
            WHERE id = ? AND user_id = ?
        """, (new_amount_paid, new_balance_due, new_invoice_status, invoice_id, current_user.id))
        
        conn.commit()
        
        return jsonify({
            "success": True,
            "message": "Payment reversed successfully",
            "invoice_id": invoice_id,
            "new_amount_paid": new_amount_paid,
            "new_balance_due": new_balance_due,
            "new_status": new_invoice_status,
            "reversed_payment": {
                "id": payment_id,
                "amount": amount,
                "reversal_reason": reversal_reason,
                "reversed_at": datetime.now().isoformat()
            }
        }), 200
        
    except Exception as e:
        if conn:
            conn.rollback()
        print(f"Error reversing payment: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500
        
    finally:
        if cursor:
            cursor.close()
        if conn:
            try:
                conn.close()
            except:
                pass

@app.route('/api/payments/<int:payment_id>/reapply', methods=['POST'])
@login_required
def reapply_payment(payment_id):
    conn = None
    cursor = None
    
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute("""
            SELECT p.id, p.invoice_id, p.amount, p.status,
                   i.id, i.total_amount, i.amount_paid, i.balance_due, i.status,
                   i.user_id
            FROM payments p
            JOIN invoices i ON p.invoice_id = i.id
            WHERE p.id = ? AND p.user_id = ?
        """, (payment_id, current_user.id))
        
        payment = cursor.fetchone()
        
        if not payment:
            return jsonify({"error": "Payment not found"}), 404
        
        (payment_id_db, invoice_id, amount, payment_status,
         invoice_id_db, total_amount, amount_paid, balance_due, invoice_status, invoice_user_id) = payment
        
        if payment_status != 'reversed':
            return jsonify({"error": "Payment is not reversed"}), 400
        
        new_amount_paid = amount_paid + amount
        new_balance_due = total_amount - new_amount_paid
        new_invoice_status = 'paid' if new_balance_due <= 0 else ('pending' if invoice_status != 'draft' else 'draft')
        
        cursor.execute("""
            UPDATE payments 
            SET status = 'completed',
                reversed_at = NULL,
                reversed_by = NULL,
                reversal_reason = NULL
            WHERE id = ? AND user_id = ?
        """, (payment_id, current_user.id))
        
        cursor.execute("""
            UPDATE invoices 
            SET amount_paid = ?,
                balance_due = ?,
                status = ?,
                updated_at = GETDATE()
            WHERE id = ? AND user_id = ?
        """, (new_amount_paid, new_balance_due, new_invoice_status, invoice_id, current_user.id))
        
        conn.commit()
        
        return jsonify({
            "success": True,
            "message": "Payment reapplied successfully",
            "invoice_id": invoice_id,
            "new_amount_paid": new_amount_paid,
            "new_balance_due": new_balance_due,
            "new_status": new_invoice_status
        }), 200
        
    except Exception as e:
        if conn:
            conn.rollback()
        print(f"Error reapplying payment: {e}")
        return jsonify({"error": str(e)}), 500
        
    finally:
        if cursor:
            cursor.close()
        if conn:
            try:
                conn.close()
            except:
                pass

@app.route('/api/invoices/<int:invoice_id>', methods=['DELETE'])
@login_required
def delete_invoice(invoice_id):
    conn = get_db_connection()
    cursor = conn.cursor()
    
    try:
        cursor.execute("""
            SELECT status, amount_paid FROM invoices 
            WHERE id = ? AND user_id = ?
        """, (invoice_id, current_user.id))
        
        invoice = cursor.fetchone()
        
        if not invoice:
            return jsonify({"error": "Invoice not found"}), 404
        
        invoice_status = invoice[0].lower() if invoice[0] else ''
        amount_paid = float(invoice[1]) if invoice[1] else 0
        
        allowed_statuses = ['draft', 'pending', 'overdue', 'quotation', 'quote']
        
        if invoice_status not in allowed_statuses:
            return jsonify({
                "error": f"Cannot delete {invoice_status} document. Only draft, pending, overdue, or quotation documents can be deleted."
            }), 400
        
        cursor.execute("""
            SELECT COUNT(*) FROM payments 
            WHERE invoice_id = ? AND (status IS NULL OR status != 'reversed')
        """, (invoice_id,))
        
        count_result = cursor.fetchone()
        active_payment_count = count_result[0] if count_result else 0
        
        cursor.execute("""
            SELECT ISNULL(SUM(amount), 0) FROM payments 
            WHERE invoice_id = ? AND (status IS NULL OR status != 'reversed')
        """, (invoice_id,))
        
        amount_result = cursor.fetchone()
        active_payment_total = float(amount_result[0]) if amount_result and amount_result[0] else 0
        
        if invoice_status not in ['quotation', 'quote'] and active_payment_count > 0 and active_payment_total > 0:
            return jsonify({
                "error": f"Cannot delete invoice with {active_payment_count} active payment(s) totaling R{active_payment_total:.2f}. "
                         f"Please reverse all active payments first."
            }), 400
        
        cursor.execute("DELETE FROM invoice_items WHERE invoice_id = ?", (invoice_id,))
        
        cursor.execute("DELETE FROM payments WHERE invoice_id = ? AND status = 'reversed'", (invoice_id,))
        
        cursor.execute("DELETE FROM invoices WHERE id = ? AND user_id = ?", (invoice_id, current_user.id))
        
        conn.commit()
        
        return jsonify({
            "success": True, 
            "deleted": True, 
            "message": f"{'Quotation' if invoice_status in ['quotation', 'quote'] else 'Invoice'} deleted successfully"
        }), 200
        
    except Exception as e:
        conn.rollback()
        print(f"Error deleting: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500
        
    finally:
        conn.close()

@app.route('/api/invoices/<int:invoice_id>', methods=['GET'])
@login_required
def get_invoice_by_id(invoice_id):
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_invoice_operational_columns(cursor)
    conn.commit()
    
    cursor.execute("""
        SELECT i.*, c.customer_name, c.email as customer_email, c.phone as customer_phone,
               c.address as customer_address, c.city as customer_city, c.country as customer_country
        FROM invoices i
        JOIN customers c ON i.customer_id = c.id
        WHERE i.id = ? AND i.user_id = ?
    """, (invoice_id, current_user.id))
    
    invoice_row = cursor.fetchone()
    
    if not invoice_row:
        conn.close()
        return jsonify({"error": "Invoice not found"}), 404
    
    columns = [column[0] for column in cursor.description]
    invoice = {}
    for i, col in enumerate(columns):
        value = invoice_row[i]
        if isinstance(value, decimal.Decimal):
            value = float(value)
        elif hasattr(value, 'isoformat'):
            value = value.isoformat() if value else None
        invoice[col] = value
    
    cursor.execute("""
        SELECT id, item_name, description, quantity, unit_price, 
               discount_amount, tax_rate, tax_amount, total_amount, sort_order
        FROM invoice_items
        WHERE invoice_id = ?
        ORDER BY sort_order, id
    """, (invoice_id,))
    
    items = []
    for row in cursor.fetchall():
        items.append({
            "id": row[0],
            "item_name": row[1],
            "description": row[2],
            "quantity": float(row[3]) if row[3] else 0,
            "unit_price": float(row[4]) if row[4] else 0,
            "discount_amount": float(row[5]) if row[5] else 0,
            "tax_rate": float(row[6]) if row[6] else 0,
            "tax_amount": float(row[7]) if row[7] else 0,
            "total_amount": float(row[8]) if row[8] else 0,
            "sort_order": row[9] or 0
        })
    
    cursor.execute("""
        SELECT id, payment_number, payment_date, amount, payment_method, 
               reference_number, notes, status
        FROM payments
        WHERE invoice_id = ?
        ORDER BY payment_date DESC
    """, (invoice_id,))
    
    payments = []
    for row in cursor.fetchall():
        payments.append({
            "id": row[0],
            "payment_number": row[1],
            "payment_date": row[2].isoformat() if row[2] else None,
            "amount": float(row[3]) if row[3] else 0,
            "payment_method": row[4],
            "reference_number": row[5],
            "notes": row[6],
            "status": row[7]
        })
    
    conn.close()
    
    invoice['items'] = items
    invoice['payments'] = payments
    
    return jsonify(invoice), 200

@app.route('/api/invoices/<int:invoice_id>/payments', methods=['POST'])
@login_required
def record_payment(invoice_id):
    data = request.get_json()
    
    if not data.get('amount') or data['amount'] <= 0:
        return jsonify({"error": "Valid payment amount required"}), 400
    
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_invoice_operational_columns(cursor)
    conn.commit()
    
    try:
        cursor.execute("""
            SELECT id, invoice_number, total_amount, amount_paid, balance_due, status
            FROM invoices 
            WHERE id = ? AND user_id = ?
        """, (invoice_id, current_user.id))
        
        invoice = cursor.fetchone()
        
        if not invoice:
            conn.close()
            return jsonify({"error": "Invoice not found"}), 404
        
        invoice_id_db, invoice_number, total_amount, amount_paid, balance_due, status = invoice
        
        total_amount = float(total_amount)
        amount_paid = float(amount_paid) if amount_paid else 0
        balance_due = float(balance_due) if balance_due else total_amount
        
        payment_amount = float(data['amount'])
        
        if payment_amount > balance_due:
            conn.close()
            return jsonify({"error": f"Payment amount exceeds balance due of R{balance_due:.2f}"}), 400
        
        payment_number = f"PAY-{datetime.now().strftime('%Y%m%d')}-{secrets.randbelow(10000):04d}"
        payment_method = data.get('payment_method', 'cash')
        
        cursor.execute("""
            INSERT INTO payments (user_id, invoice_id, payment_number, payment_date, 
                                amount, payment_method, reference_number, notes, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'completed')
        """, (
            current_user.id,
            invoice_id,
            payment_number,
            data.get('payment_date', datetime.now().strftime('%Y-%m-%d')),
            payment_amount,
            payment_method,
            data.get('reference_number', ''),
            data.get('notes', '')
        ))
        
        new_amount_paid = amount_paid + payment_amount
        new_balance_due = total_amount - new_amount_paid
        new_status = 'paid' if new_balance_due <= 0 else status
        
        cursor.execute("""
            UPDATE invoices 
            SET amount_paid = ?,
                balance_due = ?,
                status = ?,
                payment_method = ?,
                updated_at = GETDATE()
            WHERE id = ?
        """, (new_amount_paid, new_balance_due, new_status, payment_method, invoice_id))
        
        conn.commit()
        
        payment_id = cursor.execute("SELECT @@IDENTITY").fetchone()[0]
        
        return jsonify({
            "success": True,
            "payment_id": payment_id,
            "payment_number": payment_number,
            "amount_paid": payment_amount,
            "total_paid": new_amount_paid,
            "balance_due": new_balance_due,
            "status": new_status,
            "is_fully_paid": new_status == 'paid',
            "message": "Payment recorded successfully"
        }), 201
        
    except Exception as e:
        conn.rollback()
        print(f"ERROR recording payment: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()

@app.route('/api/payments/<int:payment_id>', methods=['DELETE'])
@login_required
def delete_payment(payment_id):
    conn = None
    cursor = None
    
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute("""
            SELECT id, invoice_id, amount, status, user_id
            FROM payments 
            WHERE id = ?
        """, (payment_id,))
        
        payment_check = cursor.fetchone()
        if not payment_check:
            return jsonify({"error": "Payment not found"}), 404
        
        if payment_check[4] != current_user.id:
            return jsonify({"error": "You don't have permission to delete this payment"}), 403
        
        cursor.execute("""
            SELECT p.id, p.invoice_id, p.amount, p.status,
                   i.id as invoice_id_db, i.total_amount, i.amount_paid, i.balance_due, i.status as invoice_status,
                   i.user_id as invoice_user_id
            FROM payments p
            JOIN invoices i ON p.invoice_id = i.id
            WHERE p.id = ? AND p.user_id = ?
        """, (payment_id, current_user.id))
        
        payment = cursor.fetchone()
        
        if not payment:
            return jsonify({"error": "Payment not found"}), 404
        
        payment_id_db = payment[0]
        invoice_id = payment[1]
        payment_amount = float(payment[2]) if payment[2] else 0
        payment_status = payment[3]
        invoice_id_db = payment[4]
        total_amount = float(payment[5]) if payment[5] else 0
        amount_paid = float(payment[6]) if payment[6] else 0
        balance_due = float(payment[7]) if payment[7] else 0
        invoice_status = payment[8]
        invoice_user_id = payment[9]
        
        if invoice_user_id != current_user.id:
            return jsonify({"error": "You don't have permission to modify this invoice"}), 403
        
        if invoice_status == 'paid':
            return jsonify({"error": "Cannot delete payment from a fully paid invoice"}), 400
        
        new_amount_paid = amount_paid - payment_amount
        new_balance_due = total_amount - new_amount_paid
        new_invoice_status = 'paid' if new_balance_due <= 0 else ('pending' if invoice_status != 'draft' else 'draft')
        
        cursor.execute("DELETE FROM payments WHERE id = ? AND user_id = ?", (payment_id, current_user.id))
        
        if cursor.rowcount == 0:
            return jsonify({"error": "Payment not found"}), 404
        
        cursor.execute("""
            UPDATE invoices 
            SET amount_paid = ?,
                balance_due = ?,
                status = ?,
                updated_at = GETDATE()
            WHERE id = ? AND user_id = ?
        """, (new_amount_paid, new_balance_due, new_invoice_status, invoice_id, current_user.id))
        
        conn.commit()
        
        return jsonify({
            "success": True,
            "message": "Payment deleted successfully",
            "invoice_id": invoice_id,
            "new_amount_paid": new_amount_paid,
            "new_balance_due": new_balance_due,
            "new_status": new_invoice_status
        }), 200
        
    except Exception as e:
        if conn:
            conn.rollback()
        print(f"Error deleting payment: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500
        
    finally:
        if cursor:
            cursor.close()
        if conn:
            try:
                conn.close()
            except:
                pass

@app.route('/api/invoices/<int:invoice_id>/payments', methods=['GET'])
@login_required
def get_invoice_payments(invoice_id):
    conn = None
    cursor = None
    
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute("""
            SELECT id, payment_number, payment_date, amount, payment_method, 
                   reference_number, notes, status, created_at,
                   reversed_at, reversal_reason
            FROM payments
            WHERE invoice_id = ? AND user_id = ?
            ORDER BY payment_date DESC, created_at DESC
        """, (invoice_id, current_user.id))
        
        payments = []
        for row in cursor.fetchall():
            payments.append({
                "id": row[0],
                "payment_number": row[1],
                "payment_date": row[2].isoformat() if row[2] else None,
                "amount": float(row[3]) if row[3] else 0,
                "payment_method": row[4],
                "reference_number": row[5],
                "notes": row[6],
                "status": row[7],
                "created_at": row[8].isoformat() if row[8] else None,
                "reversed_at": row[9].isoformat() if row[9] else None,
                "reversal_reason": row[10]
            })
        
        total_amount = sum(p["amount"] for p in payments if p["status"] == "completed")
        
        return jsonify({
            "payments": payments,
            "total_payments": len(payments),
            "total_amount": total_amount
        }), 200
        
    except Exception as e:
        print(f"Error getting payments: {e}")
        return jsonify({"error": str(e)}), 500
        
    finally:
        if cursor:
            cursor.close()
        if conn:
            try:
                conn.close()
            except:
                pass

# ============================================
# PDF INVOICE GENERATION USING PDFKIT
# ============================================

@app.route('/api/invoices/<int:invoice_id>/pdf', methods=['GET'])
@login_required
def generate_invoice_pdf(invoice_id):
    conn = None
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        ensure_customer_company_columns(cursor)
        ensure_company_settings_columns(cursor)
        conn.commit()
        
        cursor.execute("SELECT invoice_template FROM users WHERE id = ?", (current_user.id,))
        template_result = cursor.fetchone()
        template_id = template_result[0] if template_result else 'modern'
        
        template_config = {
            'modern': {'file': 'pdf_invoice.html', 'name': 'Modern', 'primary': '#2196F3', 'secondary': '#FF9800'},
            'classic': {'file': 'template_classic.html', 'name': 'Classic', 'primary': '#2C3E50', 'secondary': '#34495E'},
            'corporate': {'file': 'template_corporate.html', 'name': 'Corporate', 'primary': '#1A237E', 'secondary': '#0D47A1'},
            'creative': {'file': 'template_creative.html', 'name': 'Creative', 'primary': '#9C27B0', 'secondary': '#E91E63'},
            'minimal': {'file': 'template_minimal.html', 'name': 'Minimal', 'primary': '#607D8B', 'secondary': '#78909C'},
            'elegant': {'file': 'template_elegant.html', 'name': 'Elegant', 'primary': '#800000', 'secondary': '#A52A2A'}
        }
        
        selected = template_config.get(template_id, template_config['modern'])
        
        cursor.execute("""
            SELECT 
                i.*,
                c.customer_name, c.email as customer_email, c.phone as customer_phone,
                c.address as customer_address, c.city as customer_city, c.state as customer_state,
                c.postal_code as customer_postal_code, c.country as customer_country,
                c.is_company as customer_is_company,
                c.company_registration_number as customer_company_registration_number,
                c.vat_registered as customer_vat_registered,
                c.vat_number as customer_vat_number,
                c.company_address as customer_company_address,
                cs.company_name, cs.company_logo_url, cs.company_address, cs.company_phone,
                cs.company_email, cs.company_website, cs.currency_symbol, cs.currency_code,
                cs.tax_rate_default, cs.invoice_notes_default, cs.terms_conditions_default, 
                cs.footer_text_default,
                cs.company_vat_number,
                cs.bank_account_details
            FROM invoices i
            JOIN customers c ON i.customer_id = c.id
            LEFT JOIN company_settings cs ON cs.user_id = i.user_id
            WHERE i.id = ? AND i.user_id = ?
        """, (invoice_id, current_user.id))
        
        invoice_row = cursor.fetchone()
        
        if not invoice_row:
            return jsonify({"error": "Invoice not found"}), 404
        
        columns = [column[0] for column in cursor.description]
        invoice = {}
        for i, col in enumerate(columns):
            value = invoice_row[i]
            if isinstance(value, decimal.Decimal):
                value = float(value)
            elif hasattr(value, 'isoformat'):
                value = value.isoformat() if value else None
            invoice[col] = value
        
        cursor.execute("""
            SELECT 
                id, item_name, description, quantity, unit_price, 
                discount_amount, tax_rate, tax_amount, total_amount
            FROM invoice_items
            WHERE invoice_id = ?
            ORDER BY id
        """, (invoice_id,))
        
        items = []
        for row in cursor.fetchall():
            items.append({
                "id": row[0],
                "item_name": row[1],
                "description": row[2] or "",
                "quantity": float(row[3]) if row[3] else 0,
                "unit_price": float(row[4]) if row[4] else 0,
                "discount_amount": float(row[5]) if row[5] else 0,
                "tax_rate": float(row[6]) if row[6] else 0,
                "tax_amount": float(row[7]) if row[7] else 0,
                "total_amount": float(row[8]) if row[8] else 0
            })
        
        conn.close()
        conn = None
        
        if invoice.get('company_logo_url'):
            logo_url = invoice['company_logo_url']
            if logo_url.startswith('/'):
                logo_url = logo_url[1:]
            base_dir = os.path.dirname(os.path.abspath(__file__))
            logo_abs_path = os.path.join(base_dir, logo_url)
            logo_abs_path = os.path.normpath(logo_abs_path)
            if os.path.exists(logo_abs_path):
                invoice['company_logo_url'] = f"file:///{logo_abs_path.replace(os.sep, '/')}"
            else:
                invoice['company_logo_url'] = None
        
        invoice_date_formatted = format_date(invoice.get('invoice_date', ''))
        due_date_formatted = format_date(invoice.get('due_date', ''))
        
        invoice['invoice_date_formatted'] = invoice_date_formatted
        invoice['due_date_formatted'] = due_date_formatted
        
        company_name = invoice.get('company_name', 'Broadband LifeStyle')
        
        def format_currency(amount):
            if amount is None:
                return "R0.00"
            return f"R{amount:,.2f}"
        
        html = render_template(
            selected['file'],
            invoice=invoice,
            items=items,
            company_name=company_name,
            format_currency=format_currency,
            template=selected
        )
        
        options = {
            'page-size': 'A4',
            'margin-top': '0.75in',
            'margin-right': '0.75in',
            'margin-bottom': '0.75in',
            'margin-left': '0.75in',
            'encoding': "UTF-8",
            'no-outline': None,
            'enable-local-file-access': None
        }
        
        pdf_data = pdfkit.from_string(html, False, configuration=pdfkit_config, options=options)
        
        response = make_response(pdf_data)
        response.headers['Content-Type'] = 'application/pdf'
        invoice_number = invoice.get("invoice_number") or f"INV-{invoice_id}"
        response.headers['Content-Disposition'] = f'attachment; filename=invoice_{invoice_number}.pdf'
        
        return response
        
    except Exception as e:
        if conn:
            conn.close()
        print(f"Error generating PDF: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": f"PDF generation failed: {str(e)}"}), 500

@app.route('/api/user/template', methods=['GET'])
@login_required
def get_user_template():
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT invoice_template FROM users WHERE id = ?", (current_user.id,))
    result = cursor.fetchone()
    conn.close()
    template = result[0] if result else 'modern'
    return jsonify({"template": template}), 200

@app.route('/api/user/template', methods=['PUT'])
@login_required
def update_user_template():
    data = request.get_json()
    template_id = data.get('template', 'modern')
    
    valid_templates = ['modern', 'classic', 'corporate', 'creative', 'minimal', 'elegant']
    if template_id not in valid_templates:
        return jsonify({"error": "Invalid template"}), 400
    
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("UPDATE users SET invoice_template = ? WHERE id = ?", (template_id, current_user.id))
    conn.commit()
    conn.close()
    
    return jsonify({"success": True, "template": template_id}), 200

def format_date(date_str):
    if not date_str:
        return ""
    try:
        if isinstance(date_str, str):
            dt = datetime.fromisoformat(date_str)
        else:
            dt = date_str
        return dt.strftime("%B %d, %Y")
    except:
        return date_str

# ============================================
# COMPANY SETTINGS ROUTES
# ============================================

os.makedirs(app.config['UPLOAD_FOLDER'], exist_ok=True)

@app.route('/api/company-settings', methods=['GET'])
@login_required
def get_company_settings():
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_company_settings_columns(cursor)
    conn.commit()
    
    cursor.execute("""
        SELECT * FROM company_settings WHERE user_id = ?
    """, (current_user.id,))
    
    row = cursor.fetchone()
    
    if not row:
        cursor.execute("""
            INSERT INTO company_settings (user_id, company_name, currency_symbol, currency_code, 
                                        invoice_prefix, estimate_prefix, created_at, updated_at)
            VALUES (?, '', '$', 'USD', 'INV-', 'EST-', GETDATE(), GETDATE())
        """, (current_user.id,))
        conn.commit()
        
        cursor.execute("SELECT * FROM company_settings WHERE user_id = ?", (current_user.id,))
        row = cursor.fetchone()
    
    columns = [column[0] for column in cursor.description]
    settings = {}
    for i, col in enumerate(columns):
        value = row[i]
        if isinstance(value, decimal.Decimal):
            value = float(value)
        settings[col] = value
    
    conn.close()
    
    return jsonify(settings), 200

@app.route('/api/company-settings', methods=['PUT'])
@login_required
def update_company_settings():
    data = request.get_json()
    
    if not data:
        return jsonify({"error": "No data provided"}), 400
    
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_company_settings_columns(cursor)
    conn.commit()
    
    update_fields = []
    params = []
    
    updatable_fields = [
        'company_name', 'company_logo_url', 'company_address', 'company_phone',
        'company_email', 'company_website', 'company_vat_number', 'bank_account_details', 'tax_rate_default', 'currency_symbol',
        'currency_code', 'invoice_prefix', 'estimate_prefix', 'invoice_notes_default',
        'terms_conditions_default', 'footer_text_default', 'payment_terms_default'
    ]
    
    for field in updatable_fields:
        if field in data and data[field] is not None:
            update_fields.append(f"{field} = ?")
            params.append(data[field])
    
    if not update_fields:
        conn.close()
        return jsonify({"error": "No valid fields to update"}), 400
    
    update_fields.append("updated_at = GETDATE()")
    params.append(current_user.id)
    
    query = f"UPDATE company_settings SET {', '.join(update_fields)} WHERE user_id = ?"
    cursor.execute(query, params)
    conn.commit()
    
    cursor.execute("SELECT * FROM company_settings WHERE user_id = ?", (current_user.id,))
    row = cursor.fetchone()
    columns = [column[0] for column in cursor.description]
    updated_settings = {}
    for i, col in enumerate(columns):
        value = row[i]
        if isinstance(value, decimal.Decimal):
            value = float(value)
        updated_settings[col] = value
    
    conn.close()
    
    return jsonify({
        "success": True,
        "message": "Company settings updated successfully",
        "settings": updated_settings
    }), 200

@app.route('/api/company/logo', methods=['POST'])
@login_required
def upload_company_logo():
    try:
        if 'logo' not in request.files:
            return jsonify({"error": "No file provided"}), 400
        
        file = request.files['logo']
        if file.filename == '':
            return jsonify({"error": "No file selected"}), 400
        
        allowed_extensions = {'png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp'}
        ext = file.filename.rsplit('.', 1)[1].lower() if '.' in file.filename else 'png'
        
        if ext not in allowed_extensions:
            return jsonify({"error": f"Invalid file type. Allowed: {', '.join(allowed_extensions)}"}), 400
        
        filename = f"logo_{current_user.id}_{int(time.time())}.{ext}"
        
        upload_dir = os.path.join('static', 'uploads')
        os.makedirs(upload_dir, exist_ok=True)
        filepath = os.path.join(upload_dir, filename)
        file.save(filepath)
        
        logo_url = f"/static/uploads/{filename}"
        conn = get_db_connection()
        cursor = conn.cursor()
        ensure_company_settings_columns(cursor)
        conn.commit()
        
        cursor.execute("SELECT id FROM company_settings WHERE user_id = ?", (current_user.id,))
        if cursor.fetchone():
            cursor.execute("UPDATE company_settings SET company_logo_url = ?, updated_at = GETDATE() WHERE user_id = ?", (logo_url, current_user.id))
        else:
            cursor.execute("""
                INSERT INTO company_settings (user_id, company_logo_url, created_at, updated_at)
                VALUES (?, ?, GETDATE(), GETDATE())
            """, (current_user.id, logo_url))
        
        conn.commit()
        conn.close()
        
        return jsonify({"success": True, "logo_url": logo_url}), 200
        
    except Exception as e:
        print(f"Error uploading logo: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500

@app.route('/api/company/logo', methods=['DELETE'])
@login_required
def remove_company_logo():
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        ensure_company_settings_columns(cursor)
        conn.commit()
        
        cursor.execute("SELECT company_logo_url FROM company_settings WHERE user_id = ?", (current_user.id,))
        result = cursor.fetchone()
        
        if result and result[0]:
            logo_path = result[0]
            if logo_path.startswith('/'):
                logo_path = logo_path[1:]
            
            file_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), logo_path)
            if os.path.exists(file_path):
                os.remove(file_path)
                print(f"Deleted logo file: {file_path}")
        
        cursor.execute("UPDATE company_settings SET company_logo_url = NULL, updated_at = GETDATE() WHERE user_id = ?", (current_user.id,))
        conn.commit()
        conn.close()
        
        return jsonify({"success": True, "message": "Logo removed successfully"}), 200
        
    except Exception as e:
        print(f"Error removing logo: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500

@app.route('/static/<path:filename>')
def serve_static(filename):
    from flask import send_from_directory
    return send_from_directory('static', filename)

# ============================================
# DASHBOARD STATS ROUTE
# ============================================

@app.route('/api/dashboard/stats', methods=['GET'])
@login_required
def get_dashboard_stats():
    conn = get_db_connection()
    cursor = conn.cursor()
    
    today = datetime.now().date()
    
    cursor.execute("""
        UPDATE invoices 
        SET status = 'overdue' 
        WHERE status = 'pending' 
        AND due_date < ? 
        AND balance_due > 0
        AND status NOT IN ('quotation', 'quote', 'paid')
    """, (today,))
    conn.commit()
    
    cursor.execute("""
        SELECT 
            COUNT(*) as paid_count,
            ISNULL(SUM(total_amount), 0) as paid_amount
        FROM invoices 
        WHERE user_id = ? 
        AND status = 'paid'
        AND status NOT IN ('quotation', 'quote')
    """, (current_user.id,))
    paid_result = cursor.fetchone()
    paid_count = paid_result[0] if paid_result else 0
    paid_amount = float(paid_result[1]) if paid_result else 0.0
    
    cursor.execute("""
        SELECT 
            COUNT(*) as overdue_count,
            ISNULL(SUM(balance_due), 0) as overdue_amount
        FROM invoices 
        WHERE user_id = ? 
        AND status = 'overdue'
        AND status NOT IN ('quotation', 'quote', 'draft')
    """, (current_user.id,))
    overdue_result = cursor.fetchone()
    overdue_count = overdue_result[0] if overdue_result else 0
    overdue_amount = float(overdue_result[1]) if overdue_result else 0.0
    
    cursor.execute("""
        SELECT 
            COUNT(*) as pending_count,
            ISNULL(SUM(balance_due), 0) as pending_amount
        FROM invoices 
        WHERE user_id = ? 
        AND status IN ('pending', 'overdue')
        AND status NOT IN ('quotation', 'quote', 'draft')
    """, (current_user.id,))
    pending_result = cursor.fetchone()
    pending_count = pending_result[0] if pending_result else 0
    pending_amount = float(pending_result[1]) if pending_result else 0.0
    
    cursor.execute("""
        SELECT ISNULL(SUM(amount), 0) FROM expenses 
        WHERE user_id = ?
    """, (current_user.id,))
    total_expenses = float(cursor.fetchone()[0] or 0)
    
    current_month_start = today.replace(day=1)
    cursor.execute("""
        SELECT 
            (SELECT ISNULL(SUM(total_amount), 0) FROM invoices 
             WHERE user_id = ? AND created_at >= ? AND status IN ('paid', 'pending', 'overdue')
             AND status NOT IN ('quotation', 'quote')) -
            (SELECT ISNULL(SUM(amount), 0) FROM expenses 
             WHERE user_id = ? AND expense_date >= ?) as monthly_profit
    """, (current_user.id, current_month_start, current_user.id, current_month_start))
    monthly_profit = float(cursor.fetchone()[0] or 0)
    
    cursor.execute("""
        SELECT TOP 5 i.id, i.invoice_number, i.invoice_date, i.due_date, i.status,
               i.total_amount, i.balance_due, c.customer_name
        FROM invoices i
        JOIN customers c ON i.customer_id = c.id
        WHERE i.user_id = ?
        ORDER BY i.created_at DESC
    """, (current_user.id,))
    
    recent_invoices = []
    for row in cursor.fetchall():
        recent_invoices.append({
            "id": row[0],
            "invoice_number": row[1],
            "invoice_date": row[2].isoformat() if row[2] else None,
            "due_date": row[3].isoformat() if row[3] else None,
            "status": row[4],
            "total_amount": float(row[5]) if row[5] else 0,
            "balance_due": float(row[6]) if row[6] else 0,
            "customer_name": row[7]
        })
    
    cursor.execute("""
        SELECT TOP 5 id, expense_date, category, amount, description, vendor_name
        FROM expenses
        WHERE user_id = ?
        ORDER BY expense_date DESC
    """, (current_user.id,))
    
    recent_expenses = []
    for row in cursor.fetchall():
        recent_expenses.append({
            "id": row[0],
            "expense_date": row[1].isoformat() if row[1] else None,
            "category": row[2],
            "amount": float(row[3]) if row[3] else 0,
            "description": row[4],
            "vendor_name": row[5] if len(row) > 5 else None
        })
    
    cursor.execute("""
        SELECT LOWER(status), COUNT(*) FROM invoices
        WHERE user_id = ?
        GROUP BY LOWER(status)
    """, (current_user.id,))
    
    invoice_counts = {}
    for row in cursor.fetchall():
        status = row[0] or 'unknown'
        invoice_counts[status] = row[1]

    cursor.execute("SELECT COUNT(*) FROM recurring_invoice_templates WHERE user_id = ?", (current_user.id,))
    recurring_count = cursor.fetchone()[0] or 0

    # NEW: Calculate total amount of all recurring templates
    cursor.execute("""
        SELECT ISNULL(SUM(rit.quantity * rit.unit_price - rit.discount_amount), 0) as template_total
        FROM recurring_invoice_templates rt
        JOIN recurring_invoice_template_items rit ON rt.id = rit.template_id
        WHERE rt.user_id = ?
    """, (current_user.id,))
    recurring_total_result = cursor.fetchone()
    recurring_total_amount = float(recurring_total_result[0] or 0)

    cursor.execute("""
        UPDATE invoices 
        SET status = 'pending' 
        WHERE status = 'overdue' 
        AND due_date < ? 
        AND balance_due > 0
        AND status NOT IN ('quotation', 'quote', 'paid')
    """, (today,))
    conn.commit()
    
    conn.close()
    
    return jsonify({
        "total_revenue": paid_amount,
        "pending_amount": pending_amount,
        "total_expenses": total_expenses,
        "monthly_profit": monthly_profit,
        "recent_invoices": recent_invoices,
        "recent_expenses": recent_expenses,
        "invoice_counts": invoice_counts,
        "paid_count": paid_count,
        "paid_amount": paid_amount,
        "overdue_count": overdue_count,
        "overdue_amount": overdue_amount,
        "pending_count": pending_count,
        "recurring_count": recurring_count,
        "recurring_total_amount": recurring_total_amount
    }), 200

# ============================================
# EXPENSE MANAGEMENT
# ============================================

@app.route('/api/expenses', methods=['GET'])
@login_required
def get_expenses():
    page = request.args.get('page', 1, type=int)
    per_page = request.args.get('per_page', 50, type=int)
    category = request.args.get('category', '')
    date_from = request.args.get('date_from', '')
    date_to = request.args.get('date_to', '')
    
    offset = (page - 1) * per_page
    
    conn = get_db_connection()
    cursor = conn.cursor()
    
    query = """
        SELECT id, expense_date, category, amount, description, 
               vendor_name, payment_method, receipt_image_url, is_recurring,
               recurring_period, created_at
        FROM expenses 
        WHERE user_id = ?
    """
    params = [current_user.id]
    
    if category:
        query += " AND category = ?"
        params.append(category)
    
    if date_from:
        query += " AND expense_date >= ?"
        params.append(date_from)
    
    if date_to:
        query += " AND expense_date <= ?"
        params.append(date_to)
    
    count_query = query.replace(
        "SELECT id, expense_date, category, amount, description, vendor_name, payment_method, receipt_image_url, is_recurring, recurring_period, created_at",
        "SELECT COUNT(*)"
    )
    cursor.execute(count_query, params)
    count_result = cursor.fetchone()
    total = count_result[0] if count_result and count_result[0] is not None else 0
    
    if total == 0:
        conn.close()
        return jsonify({
            "expenses": [],
            "total": 0,
            "page": page,
            "per_page": per_page,
            "total_pages": 0
        }), 200
    
    query += " ORDER BY expense_date DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"
    params.extend([offset, per_page])
    
    cursor.execute(query, params)
    
    expenses = []
    for row in cursor.fetchall():
        expenses.append({
            "id": row[0],
            "expense_date": row[1].isoformat() if row[1] else None,
            "category": row[2],
            "amount": float(row[3]) if row[3] else 0,
            "description": row[4],
            "vendor_name": row[5],
            "payment_method": row[6],
            "receipt_image_url": row[7],
            "is_recurring": bool(row[8]),
            "recurring_period": row[9],
            "created_at": row[10].isoformat() if row[10] else None
        })
    
    conn.close()
    
    return jsonify({
        "expenses": expenses,
        "total": total,
        "page": page,
        "per_page": per_page,
        "total_pages": (total + per_page - 1) // per_page
    }), 200

def ensure_expense_date_optional(cursor):
    cursor.execute("""
        IF EXISTS (
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = 'dbo'
              AND TABLE_NAME = 'expenses'
              AND COLUMN_NAME = 'expense_date'
              AND IS_NULLABLE = 'NO'
        )
        BEGIN
            ALTER TABLE dbo.expenses ALTER COLUMN expense_date DATE NULL
        END
    """)

@app.route('/api/expenses', methods=['POST'])
@login_required
def create_expense():
    data = request.get_json() or {}
    
    required_fields = ['category', 'amount']
    for field in required_fields:
        if not data.get(field):
            return jsonify({"error": f"{field} is required"}), 400
    
    conn = get_db_connection()
    cursor = conn.cursor()
    
    try:
        ensure_expense_date_optional(cursor)
        cursor.execute("""
            INSERT INTO expenses (
                user_id, expense_date, category, amount, description,
                vendor_name, payment_method, receipt_image_url, is_recurring,
                recurring_period
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            current_user.id,
            data.get('expense_date') or None,
            data['category'],
            data['amount'],
            data.get('description', ''),
            data.get('vendor_name', ''),
            data.get('payment_method', ''),
            data.get('receipt_image_url', ''),
            1 if data.get('is_recurring') else 0,
            data.get('recurring_period', '')
        ))
        
        conn.commit()
        
        return jsonify({
            "success": True,
            "expense_id": cursor.execute("SELECT @@IDENTITY").fetchone()[0],
            "message": "Expense created successfully"
        }), 201
        
    except Exception as e:
        conn.rollback()
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()

@app.route('/api/expenses/<int:expense_id>', methods=['GET'])
@login_required
def get_expense_by_id(expense_id):
    conn = get_db_connection()
    cursor = conn.cursor()

    cursor.execute("""
        SELECT id, expense_date, category, amount, description,
               vendor_name, payment_method, receipt_image_url, is_recurring,
               recurring_period, created_at
        FROM expenses
        WHERE id = ? AND user_id = ?
    """, (expense_id, current_user.id))

    row = cursor.fetchone()
    conn.close()

    if not row:
        return jsonify({"error": "Expense not found"}), 404

    return jsonify({
        "id": row[0],
        "expense_date": row[1].isoformat() if row[1] else None,
        "category": row[2],
        "amount": float(row[3]) if row[3] else 0,
        "description": row[4],
        "vendor_name": row[5],
        "payment_method": row[6],
        "receipt_image_url": row[7],
        "is_recurring": bool(row[8]),
        "recurring_period": row[9],
        "created_at": row[10].isoformat() if row[10] else None
    }), 200

@app.route('/api/expenses/<int:expense_id>', methods=['PUT'])
@login_required
def update_expense(expense_id):
    data = request.get_json() or {}

    required_fields = ['category', 'amount']
    for field in required_fields:
        if not data.get(field):
            return jsonify({"error": f"{field} is required"}), 400

    conn = get_db_connection()
    cursor = conn.cursor()

    try:
        ensure_expense_date_optional(cursor)
        cursor.execute("""
            UPDATE expenses
            SET expense_date = ?,
                category = ?,
                amount = ?,
                description = ?,
                vendor_name = ?,
                payment_method = ?,
                receipt_image_url = ?,
                is_recurring = ?,
                recurring_period = ?,
                updated_at = GETDATE()
            WHERE id = ? AND user_id = ?
        """, (
            data.get('expense_date') or None,
            data['category'],
            data['amount'],
            data.get('description', ''),
            data.get('vendor_name', ''),
            data.get('payment_method', ''),
            data.get('receipt_image_url', ''),
            1 if data.get('is_recurring') else 0,
            data.get('recurring_period', ''),
            expense_id,
            current_user.id
        ))

        if cursor.rowcount == 0:
            conn.rollback()
            return jsonify({"error": "Expense not found"}), 404

        conn.commit()
        return jsonify({"success": True, "message": "Expense updated successfully"}), 200
    except Exception as e:
        conn.rollback()
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()

@app.route('/api/expenses/<int:expense_id>', methods=['DELETE'])
@login_required
def delete_expense(expense_id):
    conn = get_db_connection()
    cursor = conn.cursor()

    cursor.execute("DELETE FROM expenses WHERE id = ? AND user_id = ?", (expense_id, current_user.id))
    deleted = cursor.rowcount
    conn.commit()
    conn.close()

    if deleted == 0:
        return jsonify({"error": "Expense not found"}), 404

    return jsonify({"success": True, "message": "Expense deleted successfully"}), 200

# ============================================
# BUDGET MANAGEMENT
# ============================================

def ensure_budgets_table(cursor):
    cursor.execute("""
        IF OBJECT_ID('dbo.budgets', 'U') IS NULL
        BEGIN
            CREATE TABLE dbo.budgets (
                id INT IDENTITY(1,1) PRIMARY KEY,
                user_id INT NOT NULL,
                category NVARCHAR(100) NOT NULL,
                budget_month INT NOT NULL,
                budget_year INT NOT NULL,
                budget_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
                notifications_enabled BIT NOT NULL DEFAULT 1,
                created_at DATETIME NOT NULL DEFAULT GETDATE(),
                updated_at DATETIME NOT NULL DEFAULT GETDATE(),
                CONSTRAINT UQ_budgets_user_category_month_year
                    UNIQUE (user_id, category, budget_month, budget_year)
            )
        END
    """)

@app.route('/api/budgets', methods=['GET'])
@login_required
def get_budgets():
    month = request.args.get('month', datetime.now().month, type=int)
    year = request.args.get('year', datetime.now().year, type=int)

    if month < 1 or month > 12:
        return jsonify({"error": "month must be between 1 and 12"}), 400

    conn = get_db_connection()
    cursor = conn.cursor()

    try:
        ensure_budgets_table(cursor)
        conn.commit()

        cursor.execute("""
            SELECT category, ISNULL(SUM(amount), 0)
            FROM expenses
            WHERE user_id = ? AND MONTH(expense_date) = ? AND YEAR(expense_date) = ?
            GROUP BY category
        """, (current_user.id, month, year))
        actual_expenses = {row[0]: float(row[1] or 0) for row in cursor.fetchall()}

        cursor.execute("""
            SELECT id, category, budget_amount, notifications_enabled
            FROM budgets
            WHERE user_id = ? AND budget_month = ? AND budget_year = ?
            ORDER BY category
        """, (current_user.id, month, year))

        budgets = []
        for row in cursor.fetchall():
            category = row[1]
            budgets.append({
                "id": row[0],
                "category": category,
                "budget_amount": float(row[2] or 0),
                "actual_amount": actual_expenses.get(category, 0),
                "notifications_enabled": bool(row[3])
            })

        return jsonify({
            "budgets": budgets,
            "actual_expenses": actual_expenses,
            "month": month,
            "year": year
        }), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()

@app.route('/api/budgets', methods=['POST'])
@login_required
def save_budget():
    data = request.get_json() or {}
    required_fields = ['category', 'budget_month', 'budget_year', 'budget_amount']
    for field in required_fields:
        if data.get(field) in (None, ''):
            return jsonify({"error": f"{field} is required"}), 400

    month = int(data['budget_month'])
    if month < 1 or month > 12:
        return jsonify({"error": "budget_month must be between 1 and 12"}), 400

    conn = get_db_connection()
    cursor = conn.cursor()

    try:
        ensure_budgets_table(cursor)
        cursor.execute("""
            UPDATE budgets
            SET budget_amount = ?,
                notifications_enabled = ?,
                updated_at = GETDATE()
            WHERE user_id = ? AND category = ? AND budget_month = ? AND budget_year = ?
        """, (
            float(data['budget_amount']),
            1 if data.get('notifications_enabled', True) else 0,
            current_user.id,
            data['category'].strip(),
            month,
            int(data['budget_year'])
        ))

        if cursor.rowcount == 0:
            cursor.execute("""
                INSERT INTO budgets (
                    user_id, category, budget_month, budget_year,
                    budget_amount, notifications_enabled, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, GETDATE(), GETDATE())
            """, (
                current_user.id,
                data['category'].strip(),
                month,
                int(data['budget_year']),
                float(data['budget_amount']),
                1 if data.get('notifications_enabled', True) else 0
            ))

        conn.commit()
        return jsonify({"success": True, "message": "Budget saved successfully"}), 200
    except Exception as e:
        conn.rollback()
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()

# ============================================
# INVOICE ITEMS MASTER ENDPOINTS
# ============================================

@app.route('/api/invoice-items', methods=['GET'])
@login_required
def get_invoice_items():
    try:
        page = request.args.get('page', 1, type=int)
        per_page = request.args.get('per_page', 50, type=int)
        search = request.args.get('search', '', type=str)
        category_id = request.args.get('category_id', type=int)
        
        conn = get_db_connection()
        cursor = conn.cursor()
        
        base_query = """
            FROM invoice_items_master i
            LEFT JOIN invoice_item_categories c ON i.category_id = c.id
            WHERE i.user_id = ? AND i.is_active = 1
        """
        params = [current_user.id]
        
        if search:
            base_query += " AND (i.item_name LIKE ? OR i.description LIKE ?)"
            search_pattern = f"%{search}%"
            params.extend([search_pattern, search_pattern])
        
        if category_id:
            base_query += " AND i.category_id = ?"
            params.append(category_id)
        
        count_query = f"SELECT COUNT(*) {base_query}"
        cursor.execute(count_query, params)
        count_result = cursor.fetchone()
        total = count_result[0] if count_result and count_result[0] is not None else 0
        total_pages = (total + per_page - 1) // per_page if total > 0 else 1
        
        offset = (page - 1) * per_page
        select_query = f"""
            SELECT 
                i.id,
                i.item_name,
                ISNULL(i.description, '') as description,
                i.unit_price,
                i.tax_rate,
                i.is_active,
                i.category_id,
                c.category_name,
                i.created_at
            {base_query}
            ORDER BY i.item_name
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
        """
        params_with_pagination = params + [offset, per_page]
        cursor.execute(select_query, params_with_pagination)
        
        items = []
        for row in cursor.fetchall():
            item = {
                "id": row[0],
                "item_name": row[1] or "",
                "description": row[2] or "",
                "unit_price": float(row[3]) if row[3] is not None else 0.0,
                "tax_rate": float(row[4]) if row[4] is not None else 0.0,
                "is_active": bool(row[5]) if row[5] is not None else True,
                "category_id": row[6],
                "category_name": row[7] if row[7] else None,
                "created_at": row[8].isoformat() if row[8] else None
            }
            items.append(item)
        
        conn.close()
        
        return jsonify({
            "items": items,
            "total": total,
            "page": page,
            "per_page": per_page,
            "total_pages": total_pages
        }), 200
        
    except Exception as e:
        print(f"Error getting invoice items: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({
            "items": [],
            "total": 0,
            "page": 1,
            "per_page": 50,
            "total_pages": 1
        }), 200

@app.route('/api/invoice-items', methods=['POST'])
@login_required
def create_invoice_item():
    try:
        data = request.get_json()
        
        item_name = data.get('item_name', '').strip()
        description = data.get('description', '').strip()
        unit_price = data.get('unit_price', 0)
        tax_rate = data.get('tax_rate', 0)
        category_id = data.get('category_id')
        
        if not item_name:
            return jsonify({"error": "Item name is required"}), 400
        
        if unit_price <= 0:
            return jsonify({"error": "Unit price must be greater than 0"}), 400
        
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute("""
            INSERT INTO invoice_items_master (user_id, item_name, description, unit_price, tax_rate, category_id, created_at, updated_at)
            OUTPUT INSERTED.id
            VALUES (?, ?, ?, ?, ?, ?, GETDATE(), GETDATE())
        """, (current_user.id, item_name, description, unit_price, tax_rate, category_id))
        
        item_id = cursor.fetchone()[0]
        conn.commit()
        conn.close()
        
        return jsonify({
            "success": True,
            "message": "Item created successfully",
            "item_id": item_id
        }), 201
        
    except Exception as e:
        print(f"Error creating invoice item: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/invoice-items/<int:item_id>', methods=['PUT'])
@login_required
def update_invoice_item(item_id):
    try:
        data = request.get_json()
        
        item_name = data.get('item_name', '').strip()
        description = data.get('description', '').strip()
        unit_price = data.get('unit_price', 0)
        tax_rate = data.get('tax_rate', 0)
        category_id = data.get('category_id')
        
        if not item_name:
            return jsonify({"error": "Item name is required"}), 400
        
        if unit_price <= 0:
            return jsonify({"error": "Unit price must be greater than 0"}), 400
        
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute("SELECT id FROM invoice_items_master WHERE id = ? AND user_id = ?", (item_id, current_user.id))
        if not cursor.fetchone():
            conn.close()
            return jsonify({"error": "Item not found"}), 404
        
        cursor.execute("""
            UPDATE invoice_items_master
            SET item_name = ?, description = ?, unit_price = ?, tax_rate = ?, category_id = ?, updated_at = GETDATE()
            WHERE id = ? AND user_id = ?
        """, (item_name, description, unit_price, tax_rate, category_id, item_id, current_user.id))
        
        conn.commit()
        conn.close()
        
        return jsonify({
            "success": True,
            "message": "Item updated successfully"
        }), 200
        
    except Exception as e:
        print(f"Error updating invoice item: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/invoice-items/<int:item_id>', methods=['DELETE'])
@login_required
def delete_invoice_item(item_id):
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute("""
            UPDATE invoice_items_master
            SET is_active = 0, updated_at = GETDATE()
            WHERE id = ? AND user_id = ?
        """, (item_id, current_user.id))
        
        conn.commit()
        conn.close()
        
        return jsonify({
            "success": True,
            "message": "Item deleted successfully"
        }), 200
        
    except Exception as e:
        print(f"Error deleting invoice item: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/invoice-item-categories', methods=['GET'])
@login_required
def get_invoice_item_categories():
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute("""
            SELECT id, category_name
            FROM invoice_item_categories
            WHERE user_id = ?
            ORDER BY category_name
        """, (current_user.id,))
        
        categories = [{"id": row[0], "category_name": row[1]} for row in cursor.fetchall()]
        conn.close()
        
        return jsonify(categories), 200
        
    except Exception as e:
        print(f"Error getting categories: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/invoice-item-categories', methods=['POST'])
@login_required
def create_invoice_item_category():
    try:
        data = request.get_json()
        category_name = data.get('category_name', '').strip()
        
        if not category_name:
            return jsonify({"error": "Category name is required"}), 400
        
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute("""
            INSERT INTO invoice_item_categories (user_id, category_name, created_at)
            OUTPUT INSERTED.id
            VALUES (?, ?, GETDATE())
        """, (current_user.id, category_name))
        
        category_id = cursor.fetchone()[0]
        conn.commit()
        conn.close()
        
        return jsonify({
            "success": True,
            "message": "Category created successfully",
            "category_id": category_id,
            "category_name": category_name
        }), 201
        
    except Exception as e:
        print(f"Error creating category: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/invoice-item-categories/<int:category_id>', methods=['PUT'])
@login_required
def update_invoice_item_category(category_id):
    try:
        data = request.get_json()
        category_name = data.get('category_name', '').strip()
        
        if not category_name:
            return jsonify({"error": "Category name is required"}), 400
        
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute(
            "SELECT id FROM invoice_item_categories WHERE id = ? AND user_id = ?",
            (category_id, current_user.id)
        )
        if not cursor.fetchone():
            conn.close()
            return jsonify({"error": "Category not found"}), 404
        
        cursor.execute(
            "SELECT id FROM invoice_item_categories WHERE user_id = ? AND category_name = ? AND id != ?",
            (current_user.id, category_name, category_id)
        )
        if cursor.fetchone():
            conn.close()
            return jsonify({"error": "Category name already exists"}), 400
        
        cursor.execute("""
            UPDATE invoice_item_categories
            SET category_name = ?
            WHERE id = ? AND user_id = ?
        """, (category_name, category_id, current_user.id))
        
        conn.commit()
        conn.close()
        
        return jsonify({
            "success": True,
            "message": "Category updated successfully"
        }), 200
        
    except Exception as e:
        print(f"Error updating category: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/invoice-item-categories/<int:category_id>', methods=['DELETE'])
@login_required
def delete_invoice_item_category(category_id):
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute(
            "SELECT id FROM invoice_item_categories WHERE id = ? AND user_id = ?",
            (category_id, current_user.id)
        )
        if not cursor.fetchone():
            conn.close()
            return jsonify({"error": "Category not found"}), 404
        
        cursor.execute("""
            UPDATE invoice_items_master
            SET category_id = NULL, updated_at = GETDATE()
            WHERE category_id = ? AND user_id = ?
        """, (category_id, current_user.id))
        
        cursor.execute(
            "DELETE FROM invoice_item_categories WHERE id = ? AND user_id = ?",
            (category_id, current_user.id)
        )
        
        conn.commit()
        conn.close()
        
        return jsonify({
            "success": True,
            "message": "Category deleted successfully"
        }), 200
        
    except Exception as e:
        print(f"Error deleting category: {e}")
        return jsonify({"error": str(e)}), 500

# ==================== INVOICE COMMUNICATION ENDPOINTS ====================

def communication_row_to_dict(row):
    return {
        "id": row[0],
        "invoice_id": row[1],
        "invoice_number": row[2],
        "customer_id": row[3],
        "customer_name": row[4],
        "customer_email": row[5],
        "communication_type": row[6],
        "subject": row[7],
        "message_body": row[8],
        "sent_at": row[9].isoformat() if row[9] else None,
        "is_read_by_customer": bool(row[10]),
        "read_at": row[11].isoformat() if row[11] else None,
        "customer_replied": bool(row[12]),
        "reply_at": row[13].isoformat() if row[13] else None,
        "reply_body": row[14]
    }

def communication_filter_clause(filter_name):
    if filter_name == "sent":
        return "AND ic.communication_type IN ('sent', 'follow_up', 'reply')"
    if filter_name == "opened":
        return "AND ic.is_read_by_customer = 1"
    if filter_name == "replied":
        return """
            AND (
                ic.communication_type = 'customer_reply'
                OR (ic.customer_replied = 1 AND ic.reply_body IS NOT NULL)
            )
        """
    return ""

@app.route('/api/invoices/communications', methods=['GET'])
@login_required
def get_all_invoice_communications():
    try:
        filter_name = (request.args.get('filter') or 'all').lower()
        if filter_name not in ['all', 'sent', 'opened', 'replied']:
            return jsonify({"error": "Invalid communication filter"}), 400

        conn = get_db_connection()
        cursor = conn.cursor()

        cursor.execute(f"""
            SELECT
                ic.id,
                ic.invoice_id,
                i.invoice_number,
                ic.customer_id,
                c.customer_name,
                c.email as customer_email,
                ic.communication_type,
                ic.subject,
                ic.message_body,
                ic.sent_at,
                ic.is_read_by_customer,
                ic.read_at,
                ic.customer_replied,
                ic.reply_at,
                ic.reply_body
            FROM invoice_communications ic
            JOIN invoices i ON ic.invoice_id = i.id
            JOIN customers c ON ic.customer_id = c.id
            WHERE i.user_id = ?
            {communication_filter_clause(filter_name)}
            ORDER BY COALESCE(ic.reply_at, ic.sent_at) DESC
        """, (current_user.id,))

        communications = [communication_row_to_dict(row) for row in cursor.fetchall()]
        conn.close()
        return jsonify(communications), 200

    except Exception as e:
        print(f"Error getting all communications: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/invoices/<int:invoice_id>/communications', methods=['GET'])
@login_required
def get_invoice_communications(invoice_id):
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute("""
            SELECT 
                ic.id,
                ic.invoice_id,
                i.invoice_number,
                ic.customer_id,
                c.customer_name,
                c.email as customer_email,
                ic.communication_type,
                ic.subject,
                ic.message_body,
                ic.sent_at,
                ic.is_read_by_customer,
                ic.read_at,
                ic.customer_replied,
                ic.reply_at,
                ic.reply_body
            FROM invoice_communications ic
            JOIN invoices i ON ic.invoice_id = i.id
            JOIN customers c ON ic.customer_id = c.id
            WHERE ic.invoice_id = ? AND i.user_id = ?
            ORDER BY ic.sent_at DESC
        """, (invoice_id, current_user.id))
        
        communications = [communication_row_to_dict(row) for row in cursor.fetchall()]
        
        conn.close()
        return jsonify(communications), 200
        
    except Exception as e:
        print(f"Error getting communications: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/invoices/communications/<int:communication_id>/open.gif', methods=['GET'])
def track_communication_open(communication_id):
    pixel = (
        b'GIF89a\x01\x00\x01\x00\x80\x00\x00\x00\x00\x00\xff\xff\xff!'
        b'\xf9\x04\x01\x00\x00\x00\x00,\x00\x00\x00\x00\x01\x00\x01'
        b'\x00\x00\x02\x02D\x01\x00;'
    )
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute("""
            UPDATE invoice_communications
            SET is_read_by_customer = 1,
                read_at = COALESCE(read_at, GETDATE())
            WHERE id = ?
        """, (communication_id,))
        conn.commit()
        conn.close()
    except Exception as e:
        print(f"Error tracking communication open: {e}")

    response = make_response(pixel)
    response.headers['Content-Type'] = 'image/gif'
    response.headers['Cache-Control'] = 'no-store, no-cache, must-revalidate, max-age=0'
    response.headers['Pragma'] = 'no-cache'
    return response

@app.route('/api/invoices/send-email', methods=['POST'])
@login_required
def send_invoice_email():
    try:
        data = request.get_json()
        invoice_id = data.get('invoice_id')
        subject = data.get('subject')
        message = data.get('message')
        is_follow_up = data.get('is_follow_up', False)
        
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute("""
            SELECT 
                i.id, 
                i.invoice_number, 
                i.total_amount, 
                i.balance_due,
                i.status,
                c.id as customer_id, 
                c.customer_name, 
                c.email as customer_email
            FROM invoices i
            JOIN customers c ON i.customer_id = c.id
            WHERE i.id = ? AND i.user_id = ?
        """, (invoice_id, current_user.id))
        
        invoice = cursor.fetchone()
        if not invoice:
            conn.close()
            return jsonify({"error": "Invoice not found"}), 404
        
        invoice_id_db = invoice[0]
        invoice_number = invoice[1]
        invoice_amount = float(invoice[2]) if invoice[2] else 0
        balance_due = float(invoice[3]) if invoice[3] else 0
        invoice_status = invoice[4]
        customer_id = invoice[5]
        customer_name = invoice[6]
        customer_email = invoice[7]
        
        if not customer_email or '@' not in customer_email:
            conn.close()
            return jsonify({
                "success": False,
                "error": f"Customer '{customer_name}' has an invalid email address: '{customer_email}'. Please update the customer's email before sending."
            }), 400
        
        cursor.execute("""
            INSERT INTO invoice_communications 
            (invoice_id, customer_id, communication_type, subject, message_body, sent_at, created_by)
            OUTPUT INSERTED.id
            VALUES (?, ?, ?, ?, ?, GETDATE(), ?)
        """, (invoice_id, customer_id, 'follow_up' if is_follow_up else 'sent',
              subject, message, current_user.id))
        communication_id = cursor.fetchone()[0]
        conn.commit()

        tracking_url = url_for(
            'track_communication_open',
            communication_id=communication_id,
            _external=True
        )

        email_sent = send_customer_email_message(
            to_email=customer_email,
            to_name=customer_name,
            subject=subject,
            body=message,
            invoice_number=invoice_number,
            invoice_amount=balance_due if is_follow_up else invoice_amount,
            invoice_status=invoice_status,
            tracking_url=tracking_url,
            amount_label="Balance Due" if is_follow_up else "Amount"
        )
        
        if email_sent:
            if is_follow_up:
                cursor.execute("""
                    IF EXISTS (SELECT 1 FROM invoice_follow_ups WHERE invoice_id = ?)
                        UPDATE invoice_follow_ups
                        SET message_sent = 1,
                            message_sent_at = GETDATE()
                        WHERE invoice_id = ?
                    ELSE
                        INSERT INTO invoice_follow_ups
                        (invoice_id, follow_up_date, follow_up_type, message_sent, message_sent_at, completed)
                        VALUES (?, GETDATE(), 'standard', 1, GETDATE(), 0)
                """, (invoice_id, invoice_id, invoice_id))
            
            conn.commit()
        else:
            cursor.execute("DELETE FROM invoice_communications WHERE id = ?", (communication_id,))
            conn.commit()
        
        conn.close()
        
        return jsonify({
            "success": email_sent,
            "message": "Email sent successfully" if email_sent else "Failed to send email",
            "sent_to": customer_email
        }), 200
        
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500

@app.route('/api/invoices/send-reply', methods=['POST'])
@login_required
def send_reply():
    try:
        data = request.get_json()
        communication_id = data.get('communication_id')
        subject = data.get('subject')
        message = data.get('message')
        
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute("""
            SELECT ic.customer_id, ic.invoice_id, c.customer_name, c.email
            FROM invoice_communications ic
            JOIN customers c ON ic.customer_id = c.id
            WHERE ic.id = ?
        """, (communication_id,))
        
        comm = cursor.fetchone()
        if not comm:
            conn.close()
            return jsonify({"error": "Communication not found"}), 404
        
        customer_id, invoice_id, customer_name, customer_email = comm
        
        email_sent = send_customer_email_message(
            to_email=customer_email,
            to_name=customer_name,
            subject=f"Re: {subject}",
            body=message,
            is_reply=True
        )
        
        if email_sent:
            cursor.execute("""
                UPDATE invoice_communications
                SET customer_replied = 1, reply_at = GETDATE(), reply_body = ?
                WHERE id = ?
            """, (message, communication_id))
            
            cursor.execute("""
                INSERT INTO invoice_communications 
                (invoice_id, customer_id, communication_type, subject, message_body, sent_at, created_by)
                VALUES (?, ?, 'reply', ?, ?, GETDATE(), ?)
            """, (invoice_id, customer_id, subject, message, current_user.id))
            
            conn.commit()
        
        conn.close()
        
        return jsonify({
            "success": email_sent,
            "message": "Reply sent successfully" if email_sent else "Failed to send reply"
        }), 200
        
    except Exception as e:
        print(f"Error sending reply: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/invoices/communications/summary', methods=['GET'])
@login_required
def get_communication_summary():
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute("""
            SELECT COUNT(*)
            FROM invoice_communications ic
            JOIN invoices i ON ic.invoice_id = i.id
            WHERE i.user_id = ?
              AND ic.communication_type IN ('sent', 'follow_up', 'reply')
        """, (current_user.id,))
        total_sent = cursor.fetchone()[0] or 0
        
        cursor.execute("""
            SELECT COUNT(*)
            FROM invoice_communications ic
            JOIN invoices i ON ic.invoice_id = i.id
            WHERE i.user_id = ? AND ic.is_read_by_customer = 1
        """, (current_user.id,))
        opened = cursor.fetchone()[0] or 0
        
        cursor.execute("""
            SELECT COUNT(*)
            FROM invoice_communications ic
            JOIN invoices i ON ic.invoice_id = i.id
            WHERE i.user_id = ?
              AND (
                  ic.communication_type = 'customer_reply'
                  OR (ic.customer_replied = 1 AND ic.reply_body IS NOT NULL)
              )
        """, (current_user.id,))
        replied = cursor.fetchone()[0] or 0
        
        cursor.execute("""
            SELECT COUNT(*)
            FROM invoices i
            LEFT JOIN invoice_follow_ups f ON i.id = f.invoice_id
            WHERE i.user_id = ?
              AND i.status IN ('pending', 'overdue', 'sent')
              AND i.balance_due > 0
              AND (f.completed IS NULL OR f.completed = 0)
        """, (current_user.id,))
        pending_follow_ups = cursor.fetchone()[0] or 0

        cursor.execute("""
            SELECT TOP 5
                i.id,
                i.invoice_number,
                c.customer_name,
                COALESCE(ic.reply_at, ic.sent_at) as reply_at,
                COALESCE(ic.reply_body, ic.message_body) as reply_body
            FROM invoice_communications ic
            JOIN invoices i ON ic.invoice_id = i.id
            JOIN customers c ON ic.customer_id = c.id
            WHERE i.user_id = ?
              AND (
                  (ic.customer_replied = 1 AND ic.reply_body IS NOT NULL)
                  OR ic.communication_type = 'customer_reply'
              )
            ORDER BY COALESCE(ic.reply_at, ic.sent_at) DESC
        """, (current_user.id,))
        recent_replies = []
        for row in cursor.fetchall():
            recent_replies.append({
                "invoice_id": row[0],
                "invoice_number": row[1],
                "customer_name": row[2],
                "reply_at": row[3].isoformat() if row[3] else None,
                "reply_body": row[4]
            })
        
        conn.close()
        
        return jsonify({
            "total_sent": total_sent,
            "opened": opened,
            "replied": replied,
            "pending_follow_ups": pending_follow_ups,
            "recent_replies": recent_replies
        }), 200
        
    except Exception as e:
        print(f"Error getting summary: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/invoices/communications/sync-inbox', methods=['POST'])
@login_required
def sync_communications_inbox():
    try:
        data = request.get_json(silent=True) or {}
        days_back = int(data.get("days_back", 14))
        limit = int(data.get("limit", 50))
        result = sync_inbox_for_user(current_user.id, days_back=days_back, limit=limit)
        return jsonify({
            "success": True,
            "message": f"Synced inbox. Matched {result['matched']} customer replies.",
            **result
        }), 200
    except Exception as e:
        print(f"Error syncing inbox: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"success": False, "error": str(e)}), 500

@app.route('/api/invoices/communications/inbound-webhook', methods=['POST'])
def inbound_communications_webhook():
    try:
        if INBOUND_WEBHOOK_SECRET:
            provided_secret = request.headers.get("X-InvoiceApp-Webhook-Secret") or request.args.get("secret")
            if provided_secret != INBOUND_WEBHOOK_SECRET:
                return jsonify({"error": "Unauthorized"}), 401

        data = request.get_json() or {}
        user_id = data.get("user_id")
        sender_email = (data.get("from_email") or data.get("sender_email") or "").strip().lower()
        subject = data.get("subject") or ""
        body = clean_inbound_reply_body(data.get("text") or data.get("body") or data.get("message") or "")
        message_id = str(data.get("message_id") or f"webhook-{secrets.token_hex(16)}")[:300]

        if not user_id or not sender_email or not body:
            return jsonify({"error": "user_id, from_email, and body are required"}), 400

        received_at = datetime.now()
        if data.get("received_at"):
            try:
                received_at = datetime.fromisoformat(str(data["received_at"]).replace("Z", "+00:00")).replace(tzinfo=None)
            except Exception:
                received_at = datetime.now()

        conn = get_db_connection()
        cursor = conn.cursor()
        ensure_email_sync_table(cursor)
        conn.commit()

        if message_already_processed(cursor, user_id, message_id):
            conn.close()
            return jsonify({"success": True, "matched": False, "message": "Message already processed"}), 200

        matched = record_inbound_reply(cursor, user_id, message_id, sender_email, subject, body, received_at)
        conn.commit()
        conn.close()

        return jsonify({
            "success": True,
            "matched": matched,
            "message": "Inbound reply recorded" if matched else "Inbound email stored but no matching invoice communication was found"
        }), 200
    except Exception as e:
        print(f"Error processing inbound webhook: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"success": False, "error": str(e)}), 500

@app.route('/api/invoices/follow-ups/pending', methods=['GET'])
@login_required
def get_pending_follow_ups():
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute("""
            SELECT 
                i.id,
                i.invoice_number,
                c.id as customer_id,
                c.customer_name,
                c.email as customer_email,
                c.phone as customer_phone,
                i.total_amount,
                i.balance_due,
                i.due_date,
                i.status,
                DATEDIFF(day, i.due_date, GETDATE()) as days_overdue,
                CASE 
                    WHEN DATEDIFF(day, i.due_date, GETDATE()) > 30 THEN 'critical'
                    WHEN DATEDIFF(day, i.due_date, GETDATE()) > 14 THEN 'urgent'
                    WHEN DATEDIFF(day, i.due_date, GETDATE()) > 7 THEN 'overdue'
                    ELSE 'standard'
                END as follow_up_type,
                ISNULL(f.completed, 0) as completed,
                ISNULL(f.message_sent, 0) as message_sent,
                ISNULL(f.customer_responded, 0) as customer_responded,
                f.message_sent_at,
                f.responded_at,
                f.notes
            FROM invoices i
            JOIN customers c ON i.customer_id = c.id
            LEFT JOIN invoice_follow_ups f ON i.id = f.invoice_id
            WHERE i.user_id = ? 
            AND i.status IN ('pending', 'overdue', 'sent')
            AND i.balance_due > 0
            AND (f.completed IS NULL OR f.completed = 0)
            ORDER BY i.due_date ASC
        """, (current_user.id,))
        
        follow_ups = []
        for row in cursor.fetchall():
            follow_ups.append({
                "id": row[0],
                "invoice_id": row[0],
                "invoice_number": row[1],
                "customer_id": row[2],
                "customer_name": row[3],
                "customer_email": row[4],
                "customer_phone": row[5],
                "total_amount": float(row[6]) if row[6] else 0,
                "balance_due": float(row[7]) if row[7] else 0,
                "follow_up_date": row[8].isoformat() if row[8] else None,
                "status": row[9],
                "days_overdue": row[10] if row[10] else 0,
                "follow_up_type": row[11] if row[11] else 'standard',
                "completed": bool(row[12]) if row[12] is not None else False,
                "message_sent": bool(row[13]) if row[13] is not None else False,
                "customer_responded": bool(row[14]) if row[14] is not None else False,
                "message_sent_at": row[15].isoformat() if row[15] else None,
                "responded_at": row[16].isoformat() if row[16] else None,
                "notes": row[17]
            })
        
        conn.close()
        return jsonify(follow_ups), 200
        
    except Exception as e:
        print(f"Error getting follow-ups: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500

@app.route('/api/invoices/follow-ups/<int:follow_up_id>/mark-sent', methods=['POST'])
@login_required
def mark_follow_up_sent(follow_up_id):
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute("""
            SELECT f.id, f.invoice_id, f.message_sent
            FROM invoice_follow_ups f
            JOIN invoices i ON f.invoice_id = i.id
            WHERE f.id = ? AND i.user_id = ?
        """, (follow_up_id, current_user.id))
        
        follow_up = cursor.fetchone()
        
        if not follow_up:
            conn.close()
            return jsonify({"error": "Follow-up not found"}), 404
        
        if follow_up[2]:
            conn.close()
            return jsonify({"error": "Follow-up already marked as sent"}), 400
        
        cursor.execute("""
            UPDATE invoice_follow_ups
            SET message_sent = 1,
                message_sent_at = GETDATE()
            WHERE id = ?
        """, (follow_up_id,))
        
        conn.commit()
        conn.close()
        
        return jsonify({
            "success": True,
            "message": "Follow-up marked as sent successfully"
        }), 200
        
    except Exception as e:
        print(f"Error marking follow-up as sent: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/invoices/follow-ups/<int:follow_up_id>/complete', methods=['POST'])
@login_required
def complete_follow_up(follow_up_id):
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute("""
            SELECT f.id, f.invoice_id, f.completed
            FROM invoice_follow_ups f
            JOIN invoices i ON f.invoice_id = i.id
            WHERE f.id = ? AND i.user_id = ?
        """, (follow_up_id, current_user.id))
        
        follow_up = cursor.fetchone()
        
        if not follow_up:
            conn.close()
            return jsonify({"error": "Follow-up not found"}), 404
        
        if follow_up[2]:
            conn.close()
            return jsonify({"error": "Follow-up already completed"}), 400
        
        cursor.execute("""
            UPDATE invoice_follow_ups
            SET completed = 1
            WHERE id = ?
        """, (follow_up_id,))
        
        conn.commit()
        conn.close()
        
        return jsonify({
            "success": True,
            "message": "Follow-up marked as completed successfully"
        }), 200
        
    except Exception as e:
        print(f"Error completing follow-up: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/customers/<int:customer_id>/communication-thread', methods=['GET'])
@login_required
def get_customer_communication_thread(customer_id):
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute("""
            SELECT customer_name, email, phone 
            FROM customers 
            WHERE id = ? AND user_id = ?
        """, (customer_id, current_user.id))
        
        customer = cursor.fetchone()
        if not customer:
            conn.close()
            return jsonify({"error": "Customer not found"}), 404
        
        cursor.execute("""
            SELECT 
                ic.id,
                ic.invoice_id,
                i.invoice_number,
                ic.communication_type,
                ic.subject,
                ic.message_body,
                ic.sent_at,
                ic.is_read_by_customer,
                ic.read_at,
                ic.customer_replied,
                ic.reply_at,
                ic.reply_body,
                i.total_amount,
                i.status as invoice_status
            FROM invoice_communications ic
            JOIN invoices i ON ic.invoice_id = i.id
            WHERE ic.customer_id = ? AND i.user_id = ?
            ORDER BY ic.sent_at DESC
        """, (customer_id, current_user.id))
        
        communications = []
        for row in cursor.fetchall():
            communications.append({
                "id": row[0],
                "invoice_id": row[1],
                "invoice_number": row[2],
                "customer_id": customer_id,
                "customer_name": customer[0],
                "customer_email": customer[1],
                "communication_type": row[3],
                "subject": row[4],
                "message_body": row[5],
                "sent_at": row[6].isoformat() if row[6] else None,
                "is_read_by_customer": bool(row[7]),
                "read_at": row[8].isoformat() if row[8] else None,
                "customer_replied": bool(row[9]),
                "reply_at": row[10].isoformat() if row[10] else None,
                "reply_body": row[11],
                "invoice_amount": float(row[12]) if row[12] else 0,
                "invoice_status": row[13]
            })
        
        conn.close()
        
        total_communications = len(communications)
        emails_sent = sum(1 for c in communications if c["communication_type"] in ["sent", "follow_up"])
        customer_replies = sum(1 for c in communications if c["customer_replied"] or c["communication_type"] == "customer_reply")
        
        return jsonify({
            "customer_id": customer_id,
            "customer_name": customer[0],
            "customer_email": customer[1],
            "customer_phone": customer[2],
            "communications": communications,
            "summary": {
                "total_communications": total_communications,
                "emails_sent": emails_sent,
                "customer_replies": customer_replies,
                "response_rate": round((customer_replies / emails_sent * 100) if emails_sent > 0 else 0, 1)
            }
        }), 200
        
    except Exception as e:
        print(f"Error getting customer thread: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500

def send_customer_email_message(to_email, to_name, subject, body, invoice_number=None, invoice_amount=None, invoice_status=None, is_reply=False, tracking_url=None, amount_label="Amount"):
    try:
        if not to_email:
            return False
        
        if '@' not in str(to_email) or '.' not in str(to_email):
            return False
        
        to_email = str(to_email).strip().lower()
        
        tracking_pixel = ""
        if tracking_url:
            tracking_pixel = f'<img src="{tracking_url}" width="1" height="1" alt="" style="display:none;width:1px;height:1px;border:0;" />'

        if invoice_number and not is_reply:
            html_content = f"""
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: #f8f9fa; padding: 20px; border-radius: 10px;">
                    <h2 style="color: #3498db;">Broadband LifeStyle</h2>
                    <p>Dear {to_name},</p>
                    <p>{body}</p>
                    <div style="background: white; padding: 15px; border-radius: 8px; margin: 15px 0;">
                        <p><strong>Invoice #{invoice_number}</strong></p>
                        <p>{amount_label}: R{invoice_amount:,.2f}</p>
                        <p>Status: {invoice_status.upper()}</p>
                    </div>
                    <p>Best regards,<br>Broadband LifeStyle Team</p>
                    {tracking_pixel}
                </div>
            </body>
            </html>
            """
        else:
            html_content = f"""
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: #f8f9fa; padding: 20px; border-radius: 10px;">
                    <h2 style="color: #3498db;">Broadband LifeStyle</h2>
                    <p>Dear {to_name},</p>
                    <p>{body}</p>
                    <p>Best regards,<br>Broadband LifeStyle Team</p>
                    {tracking_pixel}
                </div>
            </body>
            </html>
            """
        
        msg = MIMEMultipart('alternative')
        msg["From"] = EMAIL_ADDRESS
        msg["To"] = to_email
        msg["Subject"] = subject
        msg.attach(MIMEText(html_content, "html"))
        
        with smtplib.SMTP(SMTP_SERVER, SMTP_PORT) as server:
            server.starttls()
            server.login(EMAIL_ADDRESS, EMAIL_PASSWORD)
            server.sendmail(EMAIL_ADDRESS, to_email, msg.as_string())
        
        print(f"Email sent successfully to {to_email}")
        return True
        
    except Exception as e:
        print(f"Error sending email to {to_email}: {e}")
        import traceback
        traceback.print_exc()
        return False

def decode_mime_header(value):
    if not value:
        return ""
    parts = []
    for text, encoding in decode_header(value):
        if isinstance(text, bytes):
            parts.append(text.decode(encoding or "utf-8", errors="replace"))
        else:
            parts.append(text)
    return "".join(parts).strip()

def get_email_text_body(message):
    if message.is_multipart():
        for part in message.walk():
            content_type = part.get_content_type()
            content_disposition = str(part.get("Content-Disposition") or "")
            if content_type == "text/plain" and "attachment" not in content_disposition:
                payload = part.get_payload(decode=True)
                if payload:
                    return payload.decode(part.get_content_charset() or "utf-8", errors="replace")
        for part in message.walk():
            if part.get_content_type() == "text/html":
                payload = part.get_payload(decode=True)
                if payload:
                    html = payload.decode(part.get_content_charset() or "utf-8", errors="replace")
                    return re.sub(r"<[^>]+>", " ", html)

    payload = message.get_payload(decode=True)
    if payload:
        return payload.decode(message.get_content_charset() or "utf-8", errors="replace")
    return str(message.get_payload() or "")

def clean_inbound_reply_body(body):
    body = re.sub(r"\r\n?", "\n", body or "").strip()
    cut_patterns = [
        r"\nOn .+ wrote:\s*$",
        r"\n-{2,}\s*Original Message\s*-{2,}",
        r"\nFrom:\s.+\nSent:\s.+\nTo:\s.+",
        r"\n> .+"
    ]
    cut_index = len(body)
    for pattern in cut_patterns:
        match = re.search(pattern, body, flags=re.IGNORECASE | re.DOTALL)
        if match:
            cut_index = min(cut_index, match.start())
    return body[:cut_index].strip()[:4000]

def message_already_processed(cursor, user_id, message_id):
    message_id = str(message_id or "")[:300]
    cursor.execute(
        "SELECT id FROM email_sync_log WHERE user_id = ? AND message_id = ?",
        (user_id, message_id)
    )
    return cursor.fetchone() is not None

def find_matching_communication(cursor, user_id, sender_email, subject):
    sender_email = (sender_email or "").lower()
    subject_normalized = re.sub(r"^(re|fw|fwd):\s*", "", subject or "", flags=re.IGNORECASE).strip().lower()
    invoice_number_match = re.search(r"([A-Z]{2,}[- ]?\d{3,}|\d{4,})", subject or "", flags=re.IGNORECASE)
    invoice_number_token = invoice_number_match.group(1).replace(" ", "-").lower() if invoice_number_match else ""

    cursor.execute("""
        SELECT TOP 50
            ic.id,
            ic.invoice_id,
            ic.customer_id,
            ic.subject,
            i.invoice_number
        FROM invoice_communications ic
        JOIN invoices i ON ic.invoice_id = i.id
        JOIN customers c ON ic.customer_id = c.id
        WHERE i.user_id = ?
          AND LOWER(c.email) = ?
          AND ic.communication_type IN ('sent', 'follow_up', 'reply')
        ORDER BY ic.sent_at DESC
    """, (user_id, sender_email))

    candidates = cursor.fetchall()
    if not candidates:
        return None

    for row in candidates:
        candidate_subject = re.sub(r"^(re|fw|fwd):\s*", "", row[3] or "", flags=re.IGNORECASE).strip().lower()
        invoice_number = (row[4] or "").lower()
        if subject_normalized and (subject_normalized in candidate_subject or candidate_subject in subject_normalized):
            return row
        if invoice_number_token and invoice_number and invoice_number_token in invoice_number:
            return row

    return candidates[0]

def record_inbound_reply(cursor, user_id, message_id, sender_email, subject, body, received_at):
    message_id = str(message_id or "")[:300]
    match = find_matching_communication(cursor, user_id, sender_email, subject)
    if not match:
        cursor.execute("""
            INSERT INTO email_sync_log (user_id, message_id, sender_email, subject, received_at)
            VALUES (?, ?, ?, ?, ?)
        """, (user_id, message_id, sender_email, subject, received_at))
        return False

    communication_id, invoice_id, customer_id = match[0], match[1], match[2]
    cursor.execute("""
        UPDATE invoice_communications
        SET customer_replied = 1,
            reply_at = COALESCE(reply_at, ?),
            reply_body = CASE
                WHEN reply_body IS NULL OR LTRIM(RTRIM(reply_body)) = '' THEN ?
                ELSE reply_body
            END
        WHERE id = ?
    """, (received_at, body, communication_id))

    cursor.execute("""
        INSERT INTO invoice_communications
        (invoice_id, customer_id, communication_type, subject, message_body, sent_at, created_by)
        VALUES (?, ?, 'customer_reply', ?, ?, ?, ?)
    """, (invoice_id, customer_id, subject, body, received_at, user_id))

    cursor.execute("""
        UPDATE invoice_follow_ups
        SET customer_responded = 1,
            responded_at = COALESCE(responded_at, ?)
        WHERE invoice_id = ?
    """, (received_at, invoice_id))

    cursor.execute("""
        INSERT INTO email_sync_log
        (user_id, message_id, invoice_id, communication_id, sender_email, subject, received_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
    """, (user_id, message_id, invoice_id, communication_id, sender_email, subject, received_at))
    return True

def sync_inbox_for_user(user_id, days_back=14, limit=50):
    if not EMAIL_ADDRESS or not EMAIL_PASSWORD:
        raise Exception("Email settings are missing. Configure EMAIL_ADDRESS and EMAIL_PASSWORD in Flask/.env.")

    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_email_sync_table(cursor)
    conn.commit()

    matched = 0
    processed = 0
    skipped = 0

    mail = imaplib.IMAP4_SSL(IMAP_SERVER, IMAP_PORT)
    try:
        mail.login(EMAIL_ADDRESS, EMAIL_PASSWORD)
        mail.select("INBOX")
        since_date = (datetime.now() - timedelta(days=days_back)).strftime("%d-%b-%Y")
        status, data = mail.search(None, "SINCE", since_date)
        if status != "OK":
            raise Exception("Unable to search inbox")

        message_ids = data[0].split()[-limit:]
        for imap_id in message_ids:
            status, msg_data = mail.fetch(imap_id, "(RFC822)")
            if status != "OK" or not msg_data:
                skipped += 1
                continue

            raw_message = msg_data[0][1]
            message = message_from_bytes(raw_message)
            message_id = (message.get("Message-ID") or f"imap-{imap_id.decode()}").strip()[:300]
            sender_name, sender_email = parseaddr(message.get("From") or "")
            sender_email = sender_email.lower()
            if sender_email == (EMAIL_ADDRESS or "").lower():
                skipped += 1
                continue
            if message_already_processed(cursor, user_id, message_id):
                skipped += 1
                continue

            subject = decode_mime_header(message.get("Subject"))
            try:
                parsed_date = parsedate_to_datetime(message.get("Date"))
                received_at = parsed_date.replace(tzinfo=None) if parsed_date else datetime.now()
            except Exception:
                received_at = datetime.now()

            body = clean_inbound_reply_body(get_email_text_body(message))
            if not body:
                skipped += 1
                continue

            processed += 1
            if record_inbound_reply(cursor, user_id, message_id, sender_email, subject, body, received_at):
                matched += 1

        conn.commit()
        return {"processed": processed, "matched": matched, "skipped": skipped}
    except Exception:
        conn.rollback()
        raise
    finally:
        try:
            mail.logout()
        except Exception:
            pass
        conn.close()

# ============================================
# REPORTS
# ============================================

@app.route('/api/reports/profit-loss', methods=['GET'])
@login_required
def get_profit_loss_report():
    start_date = request.args.get('start_date')
    end_date = request.args.get('end_date')

    if not start_date or not end_date:
        return jsonify({"error": "start_date and end_date are required"}), 400

    conn = get_db_connection()
    cursor = conn.cursor()

    try:
        cursor.execute("""
            SELECT ISNULL(SUM(total_amount), 0)
            FROM invoices
            WHERE user_id = ?
              AND status = 'paid'
              AND invoice_date >= ?
              AND invoice_date <= ?
        """, (current_user.id, start_date, end_date))
        total_revenue = float(cursor.fetchone()[0] or 0)

        cursor.execute("""
            SELECT ISNULL(SUM(amount), 0)
            FROM expenses
            WHERE user_id = ?
              AND expense_date >= ?
              AND expense_date <= ?
        """, (current_user.id, start_date, end_date))
        total_expenses = float(cursor.fetchone()[0] or 0)

        cursor.execute("""
            SELECT YEAR(invoice_date) AS revenue_year,
                   MONTH(invoice_date) AS revenue_month,
                   ISNULL(SUM(total_amount), 0) AS revenue
            FROM invoices
            WHERE user_id = ?
              AND status = 'paid'
              AND invoice_date >= ?
              AND invoice_date <= ?
            GROUP BY YEAR(invoice_date), MONTH(invoice_date)
            ORDER BY YEAR(invoice_date), MONTH(invoice_date)
        """, (current_user.id, start_date, end_date))
        revenue_by_month = [{
            "year": row[0],
            "month": row[1],
            "revenue": float(row[2] or 0)
        } for row in cursor.fetchall()]

        cursor.execute("""
            SELECT category, ISNULL(SUM(amount), 0)
            FROM expenses
            WHERE user_id = ?
              AND expense_date >= ?
              AND expense_date <= ?
            GROUP BY category
            ORDER BY ISNULL(SUM(amount), 0) DESC
        """, (current_user.id, start_date, end_date))
        expenses_by_category = [{
            "category": row[0],
            "amount": float(row[1] or 0)
        } for row in cursor.fetchall()]

        cursor.execute("""
            SELECT YEAR(invoice_date) AS invoice_year,
                   MONTH(invoice_date) AS invoice_month,
                   LOWER(status) AS status,
                   COUNT(*) AS invoice_count
            FROM invoices
            WHERE user_id = ?
              AND invoice_date >= ?
              AND invoice_date <= ?
              AND LOWER(status) IN ('paid', 'pending', 'quotation', 'quote', 'draft', 'overdue')
            GROUP BY YEAR(invoice_date), MONTH(invoice_date), LOWER(status)
            ORDER BY YEAR(invoice_date), MONTH(invoice_date)
        """, (current_user.id, start_date, end_date))

        counts_by_month = {}
        for row in cursor.fetchall():
            key = (row[0], row[1])
            if key not in counts_by_month:
                counts_by_month[key] = {
                    "year": row[0],
                    "month": row[1],
                    "paid": 0,
                    "pending": 0,
                    "quotation": 0,
                    "draft": 0,
                    "overdue": 0,
                    "total": 0
                }
            status_key = "quotation" if row[2] == "quote" else row[2]
            if status_key in counts_by_month[key]:
                counts_by_month[key][status_key] += int(row[3] or 0)
            counts_by_month[key]["total"] += int(row[3] or 0)

        invoice_counts_by_month = [
            counts_by_month[key] for key in sorted(counts_by_month.keys())
        ]

        return jsonify({
            "start_date": start_date,
            "end_date": end_date,
            "total_revenue": total_revenue,
            "total_expenses": total_expenses,
            "net_profit": total_revenue - total_expenses,
            "revenue_by_month": revenue_by_month,
            "expenses_by_category": expenses_by_category,
            "invoice_counts_by_month": invoice_counts_by_month
        }), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()

@app.route('/api/reports/sales', methods=['GET'])
@login_required
def get_sales_report():
    start_date = request.args.get('start_date')
    end_date = request.args.get('end_date')
    group_by = request.args.get('group_by', 'month')
    
    conn = get_db_connection()
    cursor = conn.cursor()
    
    if group_by == 'day':
        group_clause = "FORMAT(i.invoice_date, 'yyyy-MM-dd')"
    elif group_by == 'week':
        group_clause = "CONCAT(YEAR(i.invoice_date), '-W', DATEPART(week, i.invoice_date))"
    elif group_by == 'month':
        group_clause = "FORMAT(i.invoice_date, 'yyyy-MM')"
    elif group_by == 'quarter':
        group_clause = "CONCAT(YEAR(i.invoice_date), '-Q', DATEPART(quarter, i.invoice_date))"
    else:
        group_clause = "YEAR(i.invoice_date)"
    
    cursor.execute(f"""
        SELECT 
            {group_clause} as period,
            COUNT(*) as invoice_count,
            ISNULL(SUM(i.total_amount), 0) as total_amount,
            ISNULL(SUM(i.tax_amount), 0) as total_tax,
            ISNULL(SUM(i.discount_amount), 0) as total_discount,
            ISNULL(SUM(i.shipping_fee), 0) as total_shipping
        FROM invoices i
        WHERE i.user_id = ? 
            AND i.status = 'paid'
            AND i.invoice_date >= ?
            AND i.invoice_date <= ?
        GROUP BY {group_clause}
        ORDER BY MIN(i.invoice_date)
    """, (current_user.id, start_date, end_date))
    
    results = []
    for row in cursor.fetchall():
        results.append({
            "period": row[0],
            "invoice_count": row[1],
            "total_amount": float(row[2]),
            "total_tax": float(row[3]),
            "total_discount": float(row[4]),
            "total_shipping": float(row[5])
        })

    cursor.execute("""
        SELECT
            YEAR(created_at) AS created_year,
            MONTH(created_at) AS created_month,
            FORMAT(created_at, 'MMM yyyy') AS period,
            COUNT(*) AS invoices_created,
            SUM(CASE WHEN LOWER(status) = 'paid' THEN 1 ELSE 0 END) AS paid,
            SUM(CASE WHEN LOWER(status) = 'pending' THEN 1 ELSE 0 END) AS pending,
            SUM(CASE WHEN LOWER(status) = 'overdue' THEN 1 ELSE 0 END) AS overdue,
            ISNULL(SUM(CASE WHEN LOWER(status) = 'paid' THEN total_amount ELSE 0 END), 0) AS revenue,
            ISNULL(SUM(CASE WHEN LOWER(status) = 'paid' THEN tax_amount ELSE 0 END), 0) AS tax_liability
        FROM invoices
        WHERE user_id = ?
            AND created_at >= ?
            AND created_at <= DATEADD(day, 1, ?)
            AND LOWER(status) NOT IN ('quotation', 'quote')
        GROUP BY YEAR(created_at), MONTH(created_at), FORMAT(created_at, 'MMM yyyy')
        ORDER BY YEAR(created_at), MONTH(created_at)
    """, (current_user.id, start_date, end_date))

    monthly_activity = []
    for row in cursor.fetchall():
        monthly_activity.append({
            "year": row[0],
            "month": row[1],
            "period": row[2],
            "invoices_created": int(row[3] or 0),
            "paid": int(row[4] or 0),
            "pending": int(row[5] or 0),
            "overdue": int(row[6] or 0),
            "revenue": float(row[7] or 0),
            "tax_liability": float(row[8] or 0)
        })

    cursor.execute("""
        SELECT
            ISNULL(SUM(CASE WHEN invoice_date >= DATEADD(day, -30, CAST(GETDATE() AS date)) THEN total_amount ELSE 0 END), 0) AS last_30_days,
            ISNULL(SUM(CASE WHEN invoice_date >= DATEADD(month, -3, CAST(GETDATE() AS date)) THEN total_amount ELSE 0 END), 0) AS last_quarter,
            ISNULL(SUM(CASE WHEN invoice_date >= DATEADD(month, -6, CAST(GETDATE() AS date)) THEN total_amount ELSE 0 END), 0) AS last_six_months,
            ISNULL(SUM(CASE WHEN invoice_date >= DATEADD(year, -1, CAST(GETDATE() AS date)) THEN total_amount ELSE 0 END), 0) AS last_year
        FROM invoices
        WHERE user_id = ?
            AND status = 'paid'
    """, (current_user.id,))

    revenue_window_row = cursor.fetchone()
    revenue_windows = {
        "last_30_days": float(revenue_window_row[0] or 0) if revenue_window_row else 0,
        "last_quarter": float(revenue_window_row[1] or 0) if revenue_window_row else 0,
        "last_six_months": float(revenue_window_row[2] or 0) if revenue_window_row else 0,
        "last_year": float(revenue_window_row[3] or 0) if revenue_window_row else 0
    }
    
    cursor.execute("""
        SELECT 
            COUNT(*) as total_invoices,
            ISNULL(SUM(total_amount), 0) as total_revenue,
            ISNULL(SUM(tax_amount), 0) as total_tax
        FROM invoices
        WHERE user_id = ? 
            AND status = 'paid'
            AND invoice_date >= ?
            AND invoice_date <= ?
    """, (current_user.id, start_date, end_date))
    
    summary = cursor.fetchone()
    conn.close()
    
    return jsonify({
        "data": results,
        "monthly_activity": monthly_activity,
        "revenue_windows": revenue_windows,
        "summary": {
            "total_invoices": summary[0],
            "total_revenue": float(summary[1]),
            "total_tax": float(summary[2])
        }
    }), 200

@app.route('/api/reports/expenses', methods=['GET'])
@login_required
def get_expense_report():
    start_date = request.args.get('start_date')
    end_date = request.args.get('end_date')

    if not start_date or not end_date:
        return jsonify({"error": "start_date and end_date are required"}), 400

    conn = get_db_connection()
    cursor = conn.cursor()

    try:
        cursor.execute("""
            SELECT
                ISNULL(SUM(amount), 0) AS total_expenses,
                COUNT(*) AS expense_count,
                ISNULL(AVG(amount), 0) AS average_expense
            FROM expenses
            WHERE user_id = ?
              AND expense_date >= ?
              AND expense_date <= ?
        """, (current_user.id, start_date, end_date))
        summary_row = cursor.fetchone()

        cursor.execute("""
            SELECT
                ISNULL(NULLIF(category, ''), 'Uncategorized') AS category,
                ISNULL(SUM(amount), 0) AS amount,
                COUNT(*) AS count
            FROM expenses
            WHERE user_id = ?
              AND expense_date >= ?
              AND expense_date <= ?
            GROUP BY ISNULL(NULLIF(category, ''), 'Uncategorized')
            ORDER BY ISNULL(SUM(amount), 0) DESC
        """, (current_user.id, start_date, end_date))
        expenses_by_category = [{
            "category": row[0],
            "amount": float(row[1] or 0),
            "count": int(row[2] or 0)
        } for row in cursor.fetchall()]

        cursor.execute("""
            SELECT TOP 10
                ISNULL(NULLIF(vendor_name, ''), 'No vendor') AS vendor_name,
                ISNULL(SUM(amount), 0) AS amount,
                COUNT(*) AS count
            FROM expenses
            WHERE user_id = ?
              AND expense_date >= ?
              AND expense_date <= ?
            GROUP BY ISNULL(NULLIF(vendor_name, ''), 'No vendor')
            ORDER BY ISNULL(SUM(amount), 0) DESC
        """, (current_user.id, start_date, end_date))
        expenses_by_vendor = [{
            "vendor_name": row[0],
            "amount": float(row[1] or 0),
            "count": int(row[2] or 0)
        } for row in cursor.fetchall()]

        cursor.execute("""
            SELECT
                YEAR(expense_date) AS expense_year,
                MONTH(expense_date) AS expense_month,
                ISNULL(SUM(amount), 0) AS amount,
                COUNT(*) AS count
            FROM expenses
            WHERE user_id = ?
              AND expense_date >= ?
              AND expense_date <= ?
            GROUP BY YEAR(expense_date), MONTH(expense_date)
            ORDER BY YEAR(expense_date), MONTH(expense_date)
        """, (current_user.id, start_date, end_date))
        expenses_by_month = [{
            "year": row[0],
            "month": row[1],
            "amount": float(row[2] or 0),
            "count": int(row[3] or 0)
        } for row in cursor.fetchall()]

        return jsonify({
            "start_date": start_date,
            "end_date": end_date,
            "summary": {
                "total_expenses": float(summary_row[0] or 0) if summary_row else 0,
                "expense_count": int(summary_row[1] or 0) if summary_row else 0,
                "average_expense": float(summary_row[2] or 0) if summary_row else 0
            },
            "expenses_by_category": expenses_by_category,
            "expenses_by_vendor": expenses_by_vendor,
            "expenses_by_month": expenses_by_month
        }), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()

@app.route('/api/reports/tax', methods=['GET'])
@login_required
def get_tax_report():
    start_date = request.args.get('start_date')
    end_date = request.args.get('end_date')
    
    conn = get_db_connection()
    cursor = conn.cursor()
    
    cursor.execute("""
        SELECT 
            i.tax_rate,
            COUNT(*) as invoice_count,
            ISNULL(SUM(i.subtotal), 0) as taxable_amount,
            ISNULL(SUM(i.tax_amount), 0) as tax_collected
        FROM invoices i
        WHERE i.user_id = ? 
            AND i.status = 'paid'
            AND i.invoice_date >= ?
            AND i.invoice_date <= ?
        GROUP BY i.tax_rate
        ORDER BY i.tax_rate
    """, (current_user.id, start_date, end_date))
    
    tax_rates = []
    for row in cursor.fetchall():
        tax_rates.append({
            "tax_rate": float(row[0]) if row[0] else 0,
            "invoice_count": row[1],
            "taxable_amount": float(row[2]),
            "tax_collected": float(row[3])
        })

    cursor.execute("""
        SELECT
            YEAR(i.invoice_date) AS tax_year,
            MONTH(i.invoice_date) AS tax_month,
            FORMAT(i.invoice_date, 'MMM yyyy') AS period,
            COUNT(*) AS invoice_count,
            ISNULL(SUM(i.subtotal), 0) AS taxable_amount,
            ISNULL(SUM(i.tax_amount), 0) AS tax_liability
        FROM invoices i
        WHERE i.user_id = ?
            AND i.status = 'paid'
            AND i.invoice_date >= ?
            AND i.invoice_date <= ?
        GROUP BY YEAR(i.invoice_date), MONTH(i.invoice_date), FORMAT(i.invoice_date, 'MMM yyyy')
        ORDER BY YEAR(i.invoice_date), MONTH(i.invoice_date)
    """, (current_user.id, start_date, end_date))

    tax_by_month = []
    for row in cursor.fetchall():
        tax_by_month.append({
            "year": row[0],
            "month": row[1],
            "period": row[2],
            "invoice_count": row[3],
            "taxable_amount": float(row[4]),
            "tax_liability": float(row[5])
        })
    
    cursor.execute("""
        SELECT 
            ISNULL(SUM(ii.tax_amount), 0) as total_tax,
            COUNT(DISTINCT invoice_id) as tax_invoices
        FROM invoice_items ii
        JOIN invoices i ON ii.invoice_id = i.id
        WHERE i.user_id = ? 
            AND i.status = 'paid'
            AND i.invoice_date >= ?
            AND i.invoice_date <= ?
    """, (current_user.id, start_date, end_date))
    
    summary = cursor.fetchone()
    conn.close()
    
    return jsonify({
        "tax_rates": tax_rates,
        "tax_by_month": tax_by_month,
        "summary": {
            "total_tax_collected": float(summary[0]) if summary else 0,
            "total_tax_invoices": summary[1] if summary else 0
        }
    }), 200

@app.route('/api/reports/outstanding', methods=['GET'])
@login_required
def get_outstanding_report():
    conn = get_db_connection()
    cursor = conn.cursor()
    
    cursor.execute("""
        SELECT 
            i.id,
            i.invoice_number,
            i.invoice_date,
            i.due_date,
            i.total_amount,
            i.amount_paid,
            i.balance_due,
            c.customer_name,
            c.email as customer_email,
            c.phone as customer_phone,
            DATEDIFF(day, i.due_date, GETDATE()) as days_overdue
        FROM invoices i
        JOIN customers c ON i.customer_id = c.id
        WHERE i.user_id = ? 
            AND i.status IN ('pending', 'overdue')
            AND i.balance_due > 0
        ORDER BY i.due_date ASC
    """, (current_user.id,))
    
    outstanding = []
    total_outstanding = 0
    for row in cursor.fetchall():
        balance = float(row[6])
        total_outstanding += balance
        outstanding.append({
            "id": row[0],
            "invoice_number": row[1],
            "invoice_date": row[2].isoformat() if row[2] else None,
            "due_date": row[3].isoformat() if row[3] else None,
            "total_amount": float(row[4]),
            "amount_paid": float(row[5]),
            "balance_due": balance,
            "customer_name": row[7],
            "customer_email": row[8],
            "customer_phone": row[9],
            "days_overdue": row[10] if row[10] else 0
        })
    
    conn.close()
    
    return jsonify({
        "outstanding_invoices": outstanding,
        "total_outstanding": total_outstanding
    }), 200

@app.route('/api/reports/invoice-status', methods=['GET'])
@login_required
def get_invoice_status_report():
    start_date = request.args.get('start_date')
    end_date = request.args.get('end_date')
    
    conn = get_db_connection()
    cursor = conn.cursor()
    
    cursor.execute("""
        SELECT 
            status,
            COUNT(*) as count,
            ISNULL(SUM(total_amount), 0) as total_amount
        FROM invoices
        WHERE user_id = ? 
            AND invoice_date >= ?
            AND invoice_date <= ?
        GROUP BY status
    """, (current_user.id, start_date, end_date))
    
    status_breakdown = []
    for row in cursor.fetchall():
        status_breakdown.append({
            "status": row[0],
            "count": row[1],
            "total_amount": float(row[2])
        })

    cursor.execute("""
        SELECT
            YEAR(created_at) AS created_year,
            MONTH(created_at) AS created_month,
            FORMAT(created_at, 'MMM yyyy') AS period,
            COUNT(*) AS invoices_created,
            SUM(CASE WHEN LOWER(status) = 'paid' THEN 1 ELSE 0 END) AS paid,
            SUM(CASE WHEN LOWER(status) = 'pending' THEN 1 ELSE 0 END) AS pending,
            SUM(CASE WHEN LOWER(status) = 'overdue' THEN 1 ELSE 0 END) AS overdue,
            ISNULL(SUM(CASE WHEN LOWER(status) = 'paid' THEN total_amount ELSE 0 END), 0) AS revenue,
            ISNULL(SUM(CASE WHEN LOWER(status) = 'paid' THEN tax_amount ELSE 0 END), 0) AS tax_liability
        FROM invoices
        WHERE user_id = ?
            AND created_at >= ?
            AND created_at <= DATEADD(day, 1, ?)
            AND LOWER(status) NOT IN ('quotation', 'quote')
        GROUP BY YEAR(created_at), MONTH(created_at), FORMAT(created_at, 'MMM yyyy')
        ORDER BY YEAR(created_at), MONTH(created_at)
    """, (current_user.id, start_date, end_date))

    monthly_status = []
    for row in cursor.fetchall():
        monthly_status.append({
            "year": row[0],
            "month": row[1],
            "period": row[2],
            "invoices_created": int(row[3] or 0),
            "paid": int(row[4] or 0),
            "pending": int(row[5] or 0),
            "overdue": int(row[6] or 0),
            "revenue": float(row[7] or 0),
            "tax_liability": float(row[8] or 0)
        })
    
    conn.close()
    
    return jsonify({
        "status_breakdown": status_breakdown,
        "monthly_status": monthly_status,
        "total_invoices": sum(s["count"] for s in status_breakdown)
    }), 200

@app.route('/api/reports/item-sales', methods=['GET'])
@login_required
def get_item_sales_report():
    start_date = request.args.get('start_date')
    end_date = request.args.get('end_date')
    
    conn = get_db_connection()
    cursor = conn.cursor()
    
    cursor.execute("""
        SELECT 
            ii.item_name,
            SUM(ii.quantity) as total_quantity,
            COUNT(DISTINCT ii.invoice_id) as invoice_count,
            ISNULL(SUM(ii.total_amount), 0) as total_sales
        FROM invoice_items ii
        JOIN invoices i ON ii.invoice_id = i.id
        WHERE i.user_id = ? 
            AND i.status = 'paid'
            AND i.invoice_date >= ?
            AND i.invoice_date <= ?
        GROUP BY ii.item_name
        ORDER BY total_sales DESC
    """, (current_user.id, start_date, end_date))
    
    items = []
    for row in cursor.fetchall():
        items.append({
            "item_name": row[0],
            "quantity_sold": float(row[1]) if row[1] else 0,
            "invoice_count": row[2],
            "total_sales": float(row[3])
        })
    
    conn.close()
    
    return jsonify({
        "items": items,
        "total_items_sold": sum(i["quantity_sold"] for i in items)
    }), 200

@app.route('/api/reports/customer', methods=['GET'])
@login_required
def get_customer_report():
    start_date = request.args.get('start_date')
    end_date = request.args.get('end_date')
    
    conn = get_db_connection()
    cursor = conn.cursor()
    
    cursor.execute("""
        SELECT 
            c.id,
            c.customer_name,
            c.email,
            c.phone,
            COUNT(i.id) as invoice_count,
            ISNULL(SUM(CASE WHEN i.status = 'paid' THEN i.total_amount ELSE 0 END), 0) as total_spent,
            ISNULL(SUM(CASE WHEN i.status IN ('pending', 'overdue') THEN i.balance_due ELSE 0 END), 0) as outstanding_balance
        FROM customers c
        LEFT JOIN invoices i ON c.id = i.customer_id 
            AND i.user_id = ?
            AND i.invoice_date >= ?
            AND i.invoice_date <= ?
        WHERE c.user_id = ?
        GROUP BY c.id, c.customer_name, c.email, c.phone
        ORDER BY total_spent DESC
    """, (current_user.id, start_date, end_date, current_user.id))
    
    customers = []
    for row in cursor.fetchall():
        customers.append({
            "id": row[0],
            "customer_name": row[1],
            "email": row[2],
            "phone": row[3],
            "invoice_count": row[4],
            "total_spent": float(row[5]),
            "outstanding_balance": float(row[6])
        })
    
    conn.close()
    
    return jsonify({
        "customers": customers,
        "total_customers": len(customers)
    }), 200

# ============================================
# RUN THE APP
# ============================================

if __name__ == '__main__':
    if os.environ.get('WERKZEUG_RUN_MAIN') == 'true':
        task_manager = InvoiceTaskManager(app)
        task_manager.start()
    
    app.run(debug=True, host='0.0.0.0', port=5000)