# ============================================
# INVOICEAPP - FLASK BACKEND
# Database: InvoiceApp (SQL Server)
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
import json
import pdfkit
from datetime import datetime, timedelta
from functools import wraps
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.mime.application import MIMEApplication

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
load_dotenv()

# ============================================
# APP CONFIGURATION
# ============================================
app = Flask(__name__)
app.secret_key = os.getenv('SECRET_KEY', 'your-secret-key-change-this')

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
         "http://10.0.0.150:8080"
     ],
     allow_headers=["Content-Type", "Authorization", "X-Requested-With", "Accept", "Cookie"],
     expose_headers=["Set-Cookie", "Cookie"],
     methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"])

# After request handler for CORS headers
@app.after_request
def after_request(response):
    """Add CORS headers after each request"""
    origin = request.headers.get('Origin')
    allowed_origins = {
        'http://localhost:5000',
        'http://127.0.0.1:5000',
        'http://10.0.0.111:5000',
        'http://10.0.0.150:5000',
        'http://10.0.0.150:8080'
    }
    response.headers['Access-Control-Allow-Origin'] = origin if origin in allowed_origins else 'http://10.0.0.150:5000'
    response.headers.add('Access-Control-Allow-Headers', 'Content-Type,Authorization,Accept,Cookie')
    response.headers.add('Access-Control-Allow-Methods', 'GET,PUT,POST,DELETE,OPTIONS')
    response.headers.add('Access-Control-Allow-Credentials', 'true')
    return response

# PDF Configuration - wkhtmltopdf path
WKHTMLTOPDF_PATH = r"C:\Program Files\wkhtmltopdf\bin\wkhtmltopdf.exe"
pdfkit_config = pdfkit.configuration(wkhtmltopdf=WKHTMLTOPDF_PATH)

# Database Connection
DB_CONFIG = {
    'driver': '{ODBC Driver 17 for SQL Server}',
    'server': os.getenv('DB_SERVER', 'DESKTOP-HEACHMT\\SQLEXPRESS'),
    'database': os.getenv('DB_NAME', 'InvoiceApp'),
    'trusted_connection': 'yes'
}

def get_db_connection():
    """Get database connection"""
    conn_str = (
        f"Driver={DB_CONFIG['driver']};"
        f"Server={DB_CONFIG['server']};"
        f"Database={DB_CONFIG['database']};"
        f"Trusted_Connection={DB_CONFIG['trusted_connection']};"
    )
    return pyodbc.connect(conn_str)

def ensure_invoice_recurring_columns(cursor):
    cursor.execute("""
        IF COL_LENGTH('dbo.invoices', 'is_recurring') IS NULL
            ALTER TABLE dbo.invoices ADD is_recurring BIT NOT NULL CONSTRAINT DF_invoices_is_recurring DEFAULT 0
        IF COL_LENGTH('dbo.invoices', 'recurring_frequency') IS NULL
            ALTER TABLE dbo.invoices ADD recurring_frequency NVARCHAR(20) NULL
        IF COL_LENGTH('dbo.invoices', 'recurring_day') IS NULL
            ALTER TABLE dbo.invoices ADD recurring_day INT NULL
        IF COL_LENGTH('dbo.invoices', 'recurring_next_send_date') IS NULL
            ALTER TABLE dbo.invoices ADD recurring_next_send_date DATE NULL
        IF COL_LENGTH('dbo.invoices', 'recurring_last_sent_at') IS NULL
            ALTER TABLE dbo.invoices ADD recurring_last_sent_at DATETIME NULL
        IF COL_LENGTH('dbo.invoices', 'recurring_active') IS NULL
            ALTER TABLE dbo.invoices ADD recurring_active BIT NOT NULL CONSTRAINT DF_invoices_recurring_active DEFAULT 1
        IF COL_LENGTH('dbo.invoices', 'tax_type') IS NULL
            ALTER TABLE dbo.invoices ADD tax_type NVARCHAR(20) NOT NULL CONSTRAINT DF_invoices_tax_type DEFAULT 'exclusive'
    """)

def next_monthly_send_date(day, from_date=None):
    day = max(1, min(int(day or 1), 28))
    base = from_date or datetime.now().date()
    if isinstance(base, datetime):
        base = base.date()

    year = base.year
    month = base.month
    candidate = datetime(year, month, day).date()
    if candidate <= base:
        month += 1
        if month > 12:
            month = 1
            year += 1
        candidate = datetime(year, month, day).date()
    return candidate

def add_month_to_date(date_value):
    if isinstance(date_value, datetime):
        date_value = date_value.date()
    month = date_value.month + 1
    year = date_value.year
    if month > 12:
        month = 1
        year += 1
    day = min(date_value.day, month_calendar.monthrange(year, month)[1])
    return datetime(year, month, day).date()

# Email Configuration
SMTP_SERVER = "smtp.gmail.com"
SMTP_PORT = 587
EMAIL_ADDRESS = os.getenv("EMAIL_ADDRESS")
EMAIL_PASSWORD = os.getenv("EMAIL_PASSWORD")

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
    conn = get_db_connection()
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
    """Register a new user"""
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
    
    conn = get_db_connection()
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
    """Login user and return session token"""
    data = request.get_json()
    email = data.get('email', '').strip().lower()
    password = data.get('password', '')
    
    if not email or not password:
        return jsonify({"error": "Email and password required"}), 400
    
    conn = get_db_connection()
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
    """Send verification email"""
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
# CUSTOMER MANAGEMENT
# ============================================

@app.route('/api/customers', methods=['GET'])
@login_required
def get_customers():
    """Get all customers for the logged-in user"""
    page = request.args.get('page', 1, type=int)
    per_page = request.args.get('per_page', 20, type=int)
    search = request.args.get('search', '')
    
    offset = (page - 1) * per_page
    
    conn = get_db_connection()
    cursor = conn.cursor()
    
    query = """
        SELECT id, customer_name, email, phone, address, city, country, 
               created_at, updated_at
        FROM customers 
        WHERE user_id = ?
    """
    params = [current_user.id]
    
    if search:
        query += " AND (customer_name LIKE ? OR email LIKE ? OR phone LIKE ?)"
        search_pattern = f"%{search}%"
        params.extend([search_pattern, search_pattern, search_pattern])
    
    count_query = query.replace(
        "SELECT id, customer_name, email, phone, address, city, country, created_at, updated_at",
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
            "country": row[6],
            "created_at": row[7].isoformat() if row[7] else None,
            "updated_at": row[8].isoformat() if row[8] else None
        })
    
    conn.close()
    
    return jsonify({
        "customers": customers,
        "total": total,
        "page": page,
        "per_page": per_page,
        "total_pages": (total + per_page - 1) // per_page
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
        cursor.execute("""
            INSERT INTO customers (user_id, customer_name, email, phone, address, 
                                 city, state, postal_code, country, tax_exempt, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                tax_exempt = ?, notes = ?, updated_at = GETDATE()
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
    """Get all invoices for the logged-in user"""
    page = request.args.get('page', 1, type=int)
    per_page = request.args.get('per_page', 20, type=int)
    status = request.args.get('status', '')
    customer_id = request.args.get('customer_id', type=int)
    date_from = request.args.get('date_from', '')
    date_to = request.args.get('date_to', '')
    
    offset = (page - 1) * per_page
    today = datetime.now().date()
    
    conn = get_db_connection()
    cursor = conn.cursor()
    
    cursor.execute("""
        UPDATE invoices 
        SET status = 'overdue' 
        WHERE status = 'pending' 
        AND due_date < ? 
        AND balance_due > 0
    """, (today,))
    conn.commit()
    
    query = """
        SELECT i.id, i.invoice_number, i.invoice_date, i.due_date, i.status,
               i.subtotal, i.tax_amount, i.discount_amount, i.shipping_fee,
               i.total_amount, i.amount_paid, i.balance_due,
               c.customer_name, c.email as customer_email, i.tax_rate, i.tax_type
        FROM invoices i
        JOIN customers c ON i.customer_id = c.id
        WHERE i.user_id = ?
    """
    params = [current_user.id]
    
    if status:
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
            "tax_type": row[15] or "exclusive"
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
    """Internal function to create invoice with specified status"""
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_invoice_recurring_columns(cursor)
    conn.commit()
    
    cursor.execute("""
        SELECT ISNULL(MAX(CAST(REPLACE(invoice_number, 'INV-', '') AS INT)), 0) + 1 
        FROM invoices WHERE user_id = ?
    """, (current_user.id,))
    next_num = cursor.fetchone()[0] or 1
    invoice_number = f"INV-{next_num:06d}"
    
    subtotal = 0
    total_tax_amount = 0
    items_data = []
    tax_type = data.get('tax_type', 'exclusive')
    is_inclusive = tax_type == 'inclusive'

    for item in data['items']:
        quantity = float(item.get('quantity', 1))
        unit_price = float(item.get('unit_price', 0))
        discount = float(item.get('discount_amount', 0))
        item_tax_rate = float(item.get('tax_rate', 0))

        item_amount = (quantity * unit_price) - discount

        item_tax = 0
        if is_inclusive:
            # Formula: Tax = Total - (Total / (1 + Rate))
            item_tax = item_amount - (item_amount / (1 + (item_tax_rate / 100)))
            # For inclusive, subtotal in DB should be the net amount?
            # Actually Bookipi shows Subtotal as net if inclusive.
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

    # Total calculation
    # If inclusive, subtotal + total_tax_amount = sum of item_amounts
    gross_total = subtotal + total_tax_amount
    total_amount = gross_total - discount_amount + shipping_fee

    is_recurring = 1 if data.get('is_recurring') else 0
    recurring_frequency = data.get('recurring_frequency') or ('monthly' if is_recurring else None)
    recurring_day = int(data.get('recurring_day') or datetime.now().day)
    recurring_day = max(1, min(recurring_day, 28))
    recurring_next_send_date = next_monthly_send_date(recurring_day) if is_recurring else None
    recurring_active = 1 if data.get('recurring_active', bool(is_recurring)) and is_recurring else 0
    
    try:
        cursor.execute("""
            INSERT INTO invoices (
                user_id, customer_id, invoice_number, invoice_date, due_date,
                po_number, subtotal, tax_rate, tax_amount, discount_amount, 
                shipping_fee, total_amount, amount_paid, balance_due, notes, status,
                is_recurring, recurring_frequency, recurring_day, recurring_next_send_date,
                recurring_active, tax_type, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, GETDATE(), GETDATE())
        """, (
            current_user.id,
            data['customer_id'],
            invoice_number,
            data['invoice_date'],
            data['due_date'],
            data.get('po_number', ''),
            subtotal,
            tax_rate,
            total_tax_amount,
            discount_amount,
            shipping_fee,
            total_amount,
            total_amount,
            data.get('notes', ''),
            status,
            is_recurring,
            recurring_frequency,
            recurring_day if is_recurring else None,
            recurring_next_send_date,
            recurring_active,
            tax_type
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
        
        return jsonify({
            "success": True,
            "invoice_id": invoice_id,
            "invoice_number": invoice_number,
            "message": f"Invoice created successfully as {status}"
        }), 201
        
    except Exception as e:
        conn.rollback()
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
    
    return create_invoice_internal(data, 'pending')

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
    """Internal function to update invoice"""
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_invoice_recurring_columns(cursor)
    conn.commit()
    
    cursor.execute("""
        SELECT id, status, amount_paid, tax_rate, discount_amount, shipping_fee,
               recurring_day, recurring_next_send_date, tax_type
        FROM invoices
        WHERE id = ? AND user_id = ?
    """, 
                   (invoice_id, current_user.id))
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
        if 'tax_type' in data:
            update_fields.append("tax_type = ?")
            params.append(data.get('tax_type', 'exclusive'))
        if 'is_recurring' in data:
            is_recurring = 1 if data.get('is_recurring') else 0
            recurring_day = int(data.get('recurring_day') or invoice[6] or datetime.now().day)
            recurring_day = max(1, min(recurring_day, 28))
            recurring_next_send_date = next_monthly_send_date(recurring_day) if is_recurring else None
            update_fields.extend([
                "is_recurring = ?",
                "recurring_frequency = ?",
                "recurring_day = ?",
                "recurring_next_send_date = ?",
                "recurring_active = ?"
            ])
            params.extend([
                is_recurring,
                data.get('recurring_frequency') or ('monthly' if is_recurring else None),
                recurring_day if is_recurring else None,
                recurring_next_send_date,
                1 if data.get('recurring_active', bool(is_recurring)) and is_recurring else 0
            ])
        target_status = status or data.get('status')
        if target_status in ['draft', 'pending', 'overdue', 'paid', 'cancelled', 'quotation', 'quote']:
            update_fields.append("status = ?")
            params.append(target_status)
        
        update_fields.append("updated_at = GETDATE()")
        params.append(invoice_id)
        
        if update_fields:
            query = f"UPDATE invoices SET {', '.join(update_fields)} WHERE id = ? AND user_id = ?"
            cursor.execute(query, params + [current_user.id])

        # Totals recalculation logic
        tax_type = data.get('tax_type', invoice[8] or 'exclusive')
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
            # If no items provided in update, use existing items to recalculate totals
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

def get_invoice_email_payload(invoice_id, user_id=None):
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_invoice_recurring_columns(cursor)
    conn.commit()
    owner_id = user_id if user_id is not None else current_user.id

    cursor.execute("""
        SELECT 
            i.*,
            c.customer_name, c.email as customer_email, c.phone as customer_phone,
            c.address as customer_address, c.city as customer_city, c.state as customer_state,
            c.postal_code as customer_postal_code, c.country as customer_country,
            cs.company_name, cs.company_logo_url, cs.company_address, cs.company_phone,
            cs.company_email, cs.company_website, cs.currency_symbol, cs.currency_code,
            cs.tax_rate_default, cs.invoice_notes_default, cs.terms_conditions_default,
            cs.footer_text_default
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
    """Render the invoice HTML used for PDF downloads and invoice emails."""
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

def send_invoice_email_helper(invoice_id, user_id=None):
    """Helper function to send invoice email - renamed to avoid conflict"""
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
        ensure_invoice_recurring_columns(cursor)
        conn.commit()
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
        sent_to = send_invoice_email_helper(invoice_id)  # Changed here
        return jsonify({
            "success": True,
            "message": "Invoice sent successfully",
            "sent_to": sent_to
        }), 200
    except Exception as e:
        return jsonify({
            "success": False,
            "error": str(e),
            "message": "Invoice was saved as pending, but email sending failed"
        }), 500

def process_due_recurring_invoices():
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_invoice_recurring_columns(cursor)
    conn.commit()

    today = datetime.now().date()
    cursor.execute("""
        SELECT id, user_id, recurring_day
        FROM invoices
        WHERE is_recurring = 1
          AND recurring_active = 1
          AND status IN ('pending', 'overdue', 'paid')
          AND recurring_next_send_date IS NOT NULL
          AND recurring_next_send_date <= ?
    """, (today,))
    due_invoices = cursor.fetchall()
    conn.close()

    sent = []
    failed = []
    for row in due_invoices:
        invoice_id, user_id, recurring_day = row
        try:
            sent_to = send_invoice_email_helper(invoice_id, user_id)  # Changed here
            next_send = next_monthly_send_date(recurring_day or 1, today)

            update_conn = get_db_connection()
            update_cursor = update_conn.cursor()
            update_cursor.execute("""
                UPDATE invoices
                SET recurring_last_sent_at = GETDATE(),
                    recurring_next_send_date = ?,
                    updated_at = GETDATE()
                WHERE id = ? AND user_id = ?
            """, (next_send, invoice_id, user_id))
            update_conn.commit()
            update_conn.close()

            sent.append({"invoice_id": invoice_id, "sent_to": sent_to, "next_send_date": next_send.isoformat()})
        except Exception as e:
            failed.append({"invoice_id": invoice_id, "error": str(e)})

    return sent, failed

@app.route('/api/recurring-invoices/process-due', methods=['POST'])
@login_required
def process_due_recurring_invoices_route():
    sent, failed = process_due_recurring_invoices()
    return jsonify({
        "success": len(failed) == 0,
        "sent": sent,
        "failed": failed
    }), 200 if len(failed) == 0 else 207

def start_recurring_invoice_worker():
    def worker():
        while True:
            try:
                process_due_recurring_invoices()
            except Exception as e:
                print(f"Recurring invoice worker error: {e}")
            time.sleep(60 * 60 * 24)

    thread = threading.Thread(target=worker, daemon=True)
    thread.start()

@app.route('/api/invoices/<int:invoice_id>', methods=['DELETE'])
@login_required
def delete_invoice(invoice_id):
    conn = get_db_connection()
    cursor = conn.cursor()
    
    cursor.execute("""
        SELECT status FROM invoices 
        WHERE id = ? AND user_id = ?
    """, (invoice_id, current_user.id))
    
    invoice = cursor.fetchone()
    
    if not invoice:
        conn.close()
        return jsonify({"error": "Invoice not found"}), 404
    
    if invoice[0] not in ['draft', 'pending']:
        conn.close()
        return jsonify({"error": "Cannot delete invoice that has payments or is paid"}), 400
    
    cursor.execute("DELETE FROM invoice_items WHERE invoice_id = ?", (invoice_id,))
    cursor.execute("DELETE FROM invoices WHERE id = ?", (invoice_id,))
    
    conn.commit()
    conn.close()
    
    return jsonify({"success": True, "message": "Invoice deleted successfully"}), 200

@app.route('/api/invoices/<int:invoice_id>', methods=['GET'])
@login_required
def get_invoice_by_id(invoice_id):
    """Get a single invoice by ID with all details"""
    conn = get_db_connection()
    cursor = conn.cursor()
    ensure_invoice_recurring_columns(cursor)
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
    """Record a payment for an invoice"""
    data = request.get_json()
    
    if not data.get('amount') or data['amount'] <= 0:
        return jsonify({"error": "Valid payment amount required"}), 400
    
    conn = get_db_connection()
    cursor = conn.cursor()
    
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
        return jsonify({"error": f"Payment amount exceeds balance due of ${balance_due:.2f}"}), 400
    
    payment_number = f"PAY-{datetime.now().strftime('%Y%m%d')}-{secrets.randbelow(10000):04d}"
    
    try:
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
            data.get('payment_method', 'cash'),
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
                updated_at = GETDATE()
            WHERE id = ?
        """, (new_amount_paid, new_balance_due, new_status, invoice_id))
        
        conn.commit()
        
        return jsonify({
            "success": True,
            "payment_id": cursor.execute("SELECT @@IDENTITY").fetchone()[0],
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
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()

# ============================================
# PDF INVOICE GENERATION USING PDFKIT
# ============================================

@app.route('/api/invoices/<int:invoice_id>/pdf', methods=['GET'])
@login_required
def generate_invoice_pdf(invoice_id):
    """Generate PDF for an invoice using pdfkit with professional design"""
    conn = None
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute("""
            SELECT 
                i.*,
                c.customer_name, c.email as customer_email, c.phone as customer_phone,
                c.address as customer_address, c.city as customer_city, c.state as customer_state,
                c.postal_code as customer_postal_code, c.country as customer_country,
                cs.company_name, cs.company_logo_url, cs.company_address, cs.company_phone,
                cs.company_email, cs.company_website, cs.currency_symbol, cs.currency_code,
                cs.tax_rate_default, cs.invoice_notes_default, cs.terms_conditions_default, 
                cs.footer_text_default
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
        
        # Get invoice items
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
        
        # Format dates for display
        invoice_date_formatted = format_date(invoice.get('invoice_date', ''))
        due_date_formatted = format_date(invoice.get('due_date', ''))
        
        # Add formatted dates to invoice dict for template
        invoice['invoice_date_formatted'] = invoice_date_formatted
        invoice['due_date_formatted'] = due_date_formatted
        
        # Company name (use from settings or default)
        company_name = invoice.get('company_name', 'Broadband LifeStyle')
        
        # Format currency helper for template
        def format_currency(amount):
            if amount is None:
                return "R0.00"
            return f"R{amount:,.2f}"
        
        # Render HTML template
        html = render_template(
            'pdf_invoice.html',
            invoice=invoice,
            items=items,
            company_name=company_name,
            format_currency=format_currency
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

def format_date(date_str):
    """Format date string for display"""
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

@app.route('/api/company-settings', methods=['GET'])
@login_required
def get_company_settings():
    """Get company settings for the logged-in user"""
    conn = get_db_connection()
    cursor = conn.cursor()
    
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
    """Update company settings"""
    data = request.get_json()
    
    if not data:
        return jsonify({"error": "No data provided"}), 400
    
    conn = get_db_connection()
    cursor = conn.cursor()
    
    update_fields = []
    params = []
    
    updatable_fields = [
        'company_name', 'company_logo_url', 'company_address', 'company_phone',
        'company_email', 'company_website', 'tax_rate_default', 'currency_symbol',
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

# ============================================
# DASHBOARD STATS ROUTE
# ============================================

@app.route('/api/dashboard/stats', methods=['GET'])
@login_required
def get_dashboard_stats():
    """Get dashboard statistics"""
    conn = get_db_connection()
    cursor = conn.cursor()
    
    today = datetime.now()
    first_day = datetime(today.year, today.month, 1)
    
    cursor.execute("""
        SELECT ISNULL(SUM(total_amount), 0) 
        FROM invoices 
        WHERE user_id = ? AND status = 'paid'
    """, (current_user.id,))
    total_revenue = float(cursor.fetchone()[0] or 0)
    
    cursor.execute("""
        SELECT ISNULL(SUM(balance_due), 0) 
        FROM invoices 
        WHERE user_id = ? AND status IN ('pending', 'overdue')
    """, (current_user.id,))
    pending_amount = float(cursor.fetchone()[0] or 0)
    
    cursor.execute("""
        SELECT ISNULL(SUM(amount), 0) 
        FROM expenses 
        WHERE user_id = ?
    """, (current_user.id,))
    total_expenses = float(cursor.fetchone()[0] or 0)
    
    cursor.execute("""
        SELECT ISNULL(SUM(total_amount), 0) 
        FROM invoices 
        WHERE user_id = ? 
        AND status = 'paid'
        AND invoice_date >= ? 
        AND invoice_date <= ?
    """, (current_user.id, first_day, today))
    monthly_revenue = float(cursor.fetchone()[0] or 0)
    
    cursor.execute("""
        SELECT ISNULL(SUM(amount), 0) 
        FROM expenses 
        WHERE user_id = ? 
        AND expense_date >= ? 
        AND expense_date <= ?
    """, (current_user.id, first_day, today))
    monthly_expenses = float(cursor.fetchone()[0] or 0)
    
    monthly_profit = monthly_revenue - monthly_expenses
    
    cursor.execute("""
        SELECT status, COUNT(*) 
        FROM invoices 
        WHERE user_id = ?
        GROUP BY status
    """, (current_user.id,))
    
    invoice_counts = {}
    for row in cursor.fetchall():
        invoice_counts[row[0]] = row[1]
    
    cursor.execute("""
        SELECT TOP 5 i.id, i.invoice_number, i.invoice_date, i.total_amount, i.status,
               c.customer_name
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
            "total_amount": float(row[3]) if row[3] else 0,
            "status": row[4],
            "customer_name": row[5]
        })
    
    cursor.execute("""
        SELECT TOP 5 id, expense_date, category, amount, description
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
            "description": row[4] or ""
        })
    
    conn.close()
    
    return jsonify({
        "total_revenue": total_revenue,
        "pending_amount": pending_amount,
        "total_expenses": total_expenses,
        "monthly_revenue": monthly_revenue,
        "monthly_expenses": monthly_expenses,
        "monthly_profit": monthly_profit,
        "invoice_counts": invoice_counts,
        "recent_invoices": recent_invoices,
        "recent_expenses": recent_expenses
    }), 200

# ============================================
# EXPENSE MANAGEMENT
# ============================================

@app.route('/api/expenses', methods=['GET'])
@login_required
def get_expenses():
    """Get all expenses for the logged-in user"""
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
               vendor_name, payment_method, receipt_image_url, created_at
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
        "SELECT id, expense_date, category, amount, description, vendor_name, payment_method, receipt_image_url, created_at",
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
            "created_at": row[8].isoformat() if row[8] else None
        })
    
    conn.close()
    
    return jsonify({
        "expenses": expenses,
        "total": total,
        "page": page,
        "per_page": per_page,
        "total_pages": (total + per_page - 1) // per_page
    }), 200

@app.route('/api/expenses', methods=['POST'])
@login_required
def create_expense():
    """Create a new expense"""
    data = request.get_json()
    
    required_fields = ['expense_date', 'category', 'amount']
    for field in required_fields:
        if not data.get(field):
            return jsonify({"error": f"{field} is required"}), 400
    
    conn = get_db_connection()
    cursor = conn.cursor()
    
    try:
        cursor.execute("""
            INSERT INTO expenses (
                user_id, expense_date, category, amount, description,
                vendor_name, payment_method, receipt_image_url, is_recurring,
                recurring_period
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            current_user.id,
            data['expense_date'],
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
               vendor_name, payment_method, receipt_image_url, created_at
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
        "created_at": row[8].isoformat() if row[8] else None
    }), 200

@app.route('/api/expenses/<int:expense_id>', methods=['PUT'])
@login_required
def update_expense(expense_id):
    data = request.get_json() or {}

    required_fields = ['expense_date', 'category', 'amount']
    for field in required_fields:
        if not data.get(field):
            return jsonify({"error": f"{field} is required"}), 400

    conn = get_db_connection()
    cursor = conn.cursor()

    try:
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
            data['expense_date'],
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
    """Get all invoice items for the current user"""
    try:
        page = request.args.get('page', 1, type=int)
        per_page = request.args.get('per_page', 50, type=int)
        search = request.args.get('search', '', type=str)
        category_id = request.args.get('category_id', type=int)
        
        conn = get_db_connection()
        cursor = conn.cursor()
        
        # Build base query - FIXED to handle None results
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
        
        # Get total count - FIXED to handle None
        count_query = f"SELECT COUNT(*) {base_query}"
        cursor.execute(count_query, params)
        count_result = cursor.fetchone()
        total = count_result[0] if count_result and count_result[0] is not None else 0
        total_pages = (total + per_page - 1) // per_page if total > 0 else 1
        
        # Get paginated items
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
        
        # Build items list safely
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
        # Return empty list instead of error
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
    """Create a new invoice item"""
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
    """Update an existing invoice item"""
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
        
        # Verify item belongs to user
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
    """Soft delete an invoice item"""
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
    """Get all invoice item categories for the current user"""
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
    """Create a new invoice item category"""
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
    """Update an existing category"""
    try:
        data = request.get_json()
        category_name = data.get('category_name', '').strip()
        
        if not category_name:
            return jsonify({"error": "Category name is required"}), 400
        
        conn = get_db_connection()
        cursor = conn.cursor()
        
        # Check if category exists and belongs to user
        cursor.execute(
            "SELECT id FROM invoice_item_categories WHERE id = ? AND user_id = ?",
            (category_id, current_user.id)
        )
        if not cursor.fetchone():
            conn.close()
            return jsonify({"error": "Category not found"}), 404
        
        # Check if another category with same name exists
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
    """Delete a category (items will become uncategorized)"""
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        # Check if category exists and belongs to user
        cursor.execute(
            "SELECT id FROM invoice_item_categories WHERE id = ? AND user_id = ?",
            (category_id, current_user.id)
        )
        if not cursor.fetchone():
            conn.close()
            return jsonify({"error": "Category not found"}), 404
        
        # Set category_id to NULL for all items in this category
        cursor.execute("""
            UPDATE invoice_items_master
            SET category_id = NULL, updated_at = GETDATE()
            WHERE category_id = ? AND user_id = ?
        """, (category_id, current_user.id))
        
        # Delete the category
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

@app.route('/api/invoices/<int:invoice_id>/communications', methods=['GET'])
@login_required
def get_invoice_communications(invoice_id):
    """Get all communications for an invoice"""
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
        
        communications = []
        for row in cursor.fetchall():
            communications.append({
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
            })
        
        conn.close()
        return jsonify(communications), 200
        
    except Exception as e:
        print(f"Error getting communications: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/invoices/send-email', methods=['POST'])
@login_required
def send_invoice_email():
    """Send invoice email to customer and track communication"""
    try:
        data = request.get_json()
        invoice_id = data.get('invoice_id')
        subject = data.get('subject')
        message = data.get('message')
        is_follow_up = data.get('is_follow_up', False)
        
        conn = get_db_connection()
        cursor = conn.cursor()
        
        # Get invoice and customer details - VERIFY COLUMN ORDER
        cursor.execute("""
            SELECT 
                i.id, 
                i.invoice_number, 
                i.total_amount, 
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
        
        # IMPORTANT: Make sure we're using the correct indexes
        invoice_id_db = invoice[0]
        invoice_number = invoice[1]
        invoice_amount = float(invoice[2]) if invoice[2] else 0
        invoice_status = invoice[3]
        customer_id = invoice[4]
        customer_name = invoice[5]
        customer_email = invoice[6]  # This should be the email address
        
        # VALIDATE EMAIL - Critical fix
        if not customer_email or '@' not in customer_email:
            conn.close()
            return jsonify({
                "success": False,
                "error": f"Customer '{customer_name}' has an invalid email address: '{customer_email}'. Please update the customer's email before sending."
            }), 400
        
        # Send email using the correct email address
        email_sent = send_customer_email_message(
            to_email=customer_email,  # CRITICAL: Use customer_email, not customer_name
            to_name=customer_name,
            subject=subject,
            body=message,
            invoice_number=invoice_number,
            invoice_amount=invoice_amount,
            invoice_status=invoice_status
        )
        
        if email_sent:
            # Record communication
            cursor.execute("""
                INSERT INTO invoice_communications 
                (invoice_id, customer_id, communication_type, subject, message_body, sent_at, created_by)
                VALUES (?, ?, ?, ?, ?, GETDATE(), ?)
            """, (invoice_id, customer_id, 'follow_up' if is_follow_up else 'sent', 
                  subject, message, current_user.id))
            
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
    """Send reply to customer email"""
    try:
        data = request.get_json()
        communication_id = data.get('communication_id')
        subject = data.get('subject')
        message = data.get('message')
        
        conn = get_db_connection()
        cursor = conn.cursor()
        
        # Get original communication details
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
        
        # Send reply email
        email_sent = send_customer_email_message(
            to_email=customer_email,
            to_name=customer_name,
            subject=f"Re: {subject}",
            body=message,
            is_reply=True
        )
        
        if email_sent:
            # Record reply
            cursor.execute("""
                UPDATE invoice_communications
                SET customer_replied = 1, reply_at = GETDATE(), reply_body = ?
                WHERE id = ?
            """, (message, communication_id))
            
            # Create new communication record for the reply
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
    """Get summary of invoice communications"""
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        # Total sent invoices
        cursor.execute("""
            SELECT COUNT(*) FROM invoices WHERE user_id = ? AND status != 'draft'
        """, (current_user.id,))
        total_sent = cursor.fetchone()[0]
        
        # Opened emails (customer read)
        cursor.execute("""
            SELECT COUNT(DISTINCT ic.invoice_id)
            FROM invoice_communications ic
            JOIN invoices i ON ic.invoice_id = i.id
            WHERE i.user_id = ? AND ic.is_read_by_customer = 1
        """, (current_user.id,))
        opened = cursor.fetchone()[0] or 0
        
        # Customer replied
        cursor.execute("""
            SELECT COUNT(DISTINCT ic.invoice_id)
            FROM invoice_communications ic
            JOIN invoices i ON ic.invoice_id = i.id
            WHERE i.user_id = ? AND ic.customer_replied = 1
        """, (current_user.id,))
        replied = cursor.fetchone()[0] or 0
        
        # Pending follow-ups
        cursor.execute("""
            SELECT COUNT(*)
            FROM invoice_follow_ups f
            JOIN invoices i ON f.invoice_id = i.id
            WHERE i.user_id = ? AND f.completed = 0 AND f.follow_up_date <= GETDATE()
        """, (current_user.id,))
        pending_follow_ups = cursor.fetchone()[0] or 0
        
        conn.close()
        
        return jsonify({
            "total_sent": total_sent,
            "opened": opened,
            "replied": replied,
            "pending_follow_ups": pending_follow_ups
        }), 200
        
    except Exception as e:
        print(f"Error getting summary: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/invoices/follow-ups/pending', methods=['GET'])
@login_required
def get_pending_follow_ups():
    """Get pending follow-ups for overdue invoices"""
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        cursor.execute("""
            SELECT 
                i.id,
                i.invoice_number,
                c.customer_name,
                i.total_amount,
                i.balance_due,
                i.due_date,
                i.status,
                DATEDIFF(day, i.due_date, GETDATE()) as days_overdue
            FROM invoices i
            JOIN customers c ON i.customer_id = c.id
            WHERE i.user_id = ? 
            AND i.status IN ('pending', 'overdue')
            AND i.balance_due > 0
            ORDER BY i.due_date ASC
        """, (current_user.id,))
        
        follow_ups = []
        for row in cursor.fetchall():
            follow_ups.append({
                "id": row[0],
                "invoice_number": row[1],
                "customer_name": row[2],
                "total_amount": float(row[3]),
                "balance_due": float(row[4]),
                "due_date": row[5].isoformat() if row[5] else None,
                "status": row[6],
                "days_overdue": row[7] if row[7] else 0
            })
        
        conn.close()
        return jsonify(follow_ups), 200
        
    except Exception as e:
        print(f"Error getting follow-ups: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/customers/<int:customer_id>/communication-thread', methods=['GET'])
@login_required
def get_customer_communication_thread(customer_id):
    """Get all communications with a customer"""
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        # Get customer details
        cursor.execute("""
            SELECT customer_name, email FROM customers WHERE id = ? AND user_id = ?
        """, (customer_id, current_user.id))
        
        customer = cursor.fetchone()
        if not customer:
            conn.close()
            return jsonify({"error": "Customer not found"}), 404
        
        # Get all communications
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
                ic.customer_replied,
                ic.reply_body
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
                "communication_type": row[3],
                "subject": row[4],
                "message_body": row[5],
                "sent_at": row[6].isoformat() if row[6] else None,
                "is_read_by_customer": bool(row[7]),
                "customer_replied": bool(row[8]),
                "reply_body": row[9]
            })
        
        conn.close()
        
        return jsonify({
            "customer_id": customer_id,
            "customer_name": customer[0],
            "customer_email": customer[1],
            "communications": communications
        }), 200
        
    except Exception as e:
        print(f"Error getting customer thread: {e}")
        return jsonify({"error": str(e)}), 500

def send_customer_email_message(to_email, to_name, subject, body, invoice_number=None, invoice_amount=None, invoice_status=None, is_reply=False):
    """Helper function to send emails to customers"""
    try:
        
        # VALIDATE EMAIL
        if not to_email:
            return False
        
        if '@' not in str(to_email) or '.' not in str(to_email):
            return False
        
        to_email = str(to_email).strip().lower()
        
        # Build email content (same as before)
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
                        <p>Amount: R{invoice_amount:,.2f}</p>
                        <p>Status: {invoice_status.upper()}</p>
                    </div>
                    <p>Best regards,<br>Broadband LifeStyle Team</p>
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

# ============================================
# DEBUG ENDPOINTS (For testing)
# ============================================

@app.route('/api/debug-session', methods=['GET'])
def debug_session():
    """Debug endpoint to check session status"""
    if current_user.is_authenticated:
        return jsonify({
            "authenticated": True,
            "user_id": current_user.id,
            "email": current_user.email,
            "session_id": session.get('_id', 'N/A')
        }), 200
    else:
        return jsonify({
            "authenticated": False,
            "cookies_received": dict(request.cookies)
        }), 401

@app.route('/api/test', methods=['GET'])
def test_endpoint():
    """Test endpoint - no auth required"""
    return jsonify({
        "message": "API is working",
        "cookies": dict(request.cookies)
    }), 200

# ============================================
# RUN THE APP
# ============================================

if __name__ == '__main__':
    if os.environ.get('WERKZEUG_RUN_MAIN') == 'true':
        start_recurring_invoice_worker()
    app.run(debug=True, host='0.0.0.0', port=5000)
