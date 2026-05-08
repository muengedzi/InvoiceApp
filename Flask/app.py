# ============================================
# INVOICEAPP - FLASK BACKEND
# Database: InvoiceApp (SQL Server)
# ============================================

import os
import sys
import decimal
import re
import bcrypt
import secrets
import smtplib
import json
import pdfkit
from datetime import datetime, timedelta
from functools import wraps
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

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
               c.customer_name, c.email as customer_email
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
        "SELECT i.id, i.invoice_number, i.invoice_date, i.due_date, i.status, i.subtotal, i.tax_amount, i.discount_amount, i.shipping_fee, i.total_amount, i.amount_paid, i.balance_due, c.customer_name, c.email as customer_email",
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
            "customer_email": row[13]
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
    
    cursor.execute("""
        SELECT ISNULL(MAX(CAST(REPLACE(invoice_number, 'INV-', '') AS INT)), 0) + 1 
        FROM invoices WHERE user_id = ?
    """, (current_user.id,))
    next_num = cursor.fetchone()[0] or 1
    invoice_number = f"INV-{next_num:06d}"
    
    subtotal = 0
    items_data = []
    
    for item in data['items']:
        quantity = float(item.get('quantity', 1))
        unit_price = float(item.get('unit_price', 0))
        discount = float(item.get('discount_amount', 0))
        item_subtotal = (quantity * unit_price) - discount
        subtotal += item_subtotal
        
        items_data.append({
            'item_name': item['item_name'],
            'description': item.get('description', ''),
            'quantity': quantity,
            'unit_price': unit_price,
            'discount_amount': discount,
            'tax_rate': float(item.get('tax_rate', 0)),
            'total_amount': item_subtotal
        })
    
    tax_rate = float(data.get('tax_rate', 0))
    tax_amount = subtotal * (tax_rate / 100)
    discount_amount = float(data.get('discount_amount', 0))
    shipping_fee = float(data.get('shipping_fee', 0))
    total_amount = subtotal + tax_amount - discount_amount + shipping_fee
    
    try:
        cursor.execute("""
            INSERT INTO invoices (
                user_id, customer_id, invoice_number, invoice_date, due_date,
                po_number, subtotal, tax_rate, tax_amount, discount_amount, 
                shipping_fee, total_amount, amount_paid, balance_due, notes, status, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, GETDATE(), GETDATE())
        """, (
            current_user.id,
            data['customer_id'],
            invoice_number,
            data['invoice_date'],
            data['due_date'],
            data.get('po_number', ''),
            subtotal,
            tax_rate,
            tax_amount,
            discount_amount,
            shipping_fee,
            total_amount,
            total_amount,
            data.get('notes', ''),
            status
        ))
        
        invoice_id = cursor.execute("SELECT @@IDENTITY").fetchone()[0]
        
        for item in items_data:
            cursor.execute("""
                INSERT INTO invoice_items (
                    invoice_id, item_name, description, quantity, unit_price,
                    discount_amount, tax_rate, total_amount
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                invoice_id,
                item['item_name'],
                item['description'],
                item['quantity'],
                item['unit_price'],
                item['discount_amount'],
                item['tax_rate'],
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
    
    cursor.execute("""
        SELECT id, status, amount_paid, tax_rate, discount_amount, shipping_fee
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
        if status:
            update_fields.append("status = ?")
            params.append(status)
        
        update_fields.append("updated_at = GETDATE()")
        params.append(invoice_id)
        
        if update_fields:
            query = f"UPDATE invoices SET {', '.join(update_fields)} WHERE id = ? AND user_id = ?"
            cursor.execute(query, params + [current_user.id])
        
        if 'items' in data and data['items']:
            cursor.execute("DELETE FROM invoice_items WHERE invoice_id = ?", (invoice_id,))
            
            for item in data['items']:
                quantity = float(item.get('quantity', 1))
                unit_price = float(item.get('unit_price', 0))
                discount = float(item.get('discount_amount', 0))
                item_total = (quantity * unit_price) - discount
                
                cursor.execute("""
                    INSERT INTO invoice_items (
                        invoice_id, item_name, description, quantity, unit_price,
                        discount_amount, tax_rate, total_amount
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    invoice_id,
                    item['item_name'],
                    item.get('description', ''),
                    quantity,
                    unit_price,
                    discount,
                    item.get('tax_rate', 0),
                    item_total
                ))
        
        cursor.execute("""
            SELECT ISNULL(SUM(total_amount), 0) FROM invoice_items WHERE invoice_id = ?
        """, (invoice_id,))
        new_subtotal = float(cursor.fetchone()[0] or 0)
        
        tax_rate = float(data.get('tax_rate', invoice[3] or 0))
        tax_amount = new_subtotal * (tax_rate / 100)
        discount_amount = float(data.get('discount_amount', invoice[4] or 0))
        shipping_fee = float(data.get('shipping_fee', invoice[5] or 0))
        new_total = new_subtotal + tax_amount - discount_amount + shipping_fee
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
        """, (new_subtotal, tax_rate, tax_amount, discount_amount, shipping_fee, new_total, new_balance_due, invoice_id))
        
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

def create_invoice_pdf_html(invoice, items):
    """Create HTML content for PDF invoice"""
    
    def format_currency(amount):
        if amount is None:
            return "$0.00"
        return f"${amount:,.2f}"
    
    def format_date(date_str):
        if not date_str:
            return ""
        try:
            dt = datetime.fromisoformat(date_str)
            return dt.strftime("%B %d, %Y")
        except:
            return date_str
    
    html = f"""<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>Invoice {invoice.get('invoice_number', '')}</title>
    <style>
        @page {{
            size: A4;
            margin: 1.5cm;
        }}
        body {{
            font-family: Arial, Helvetica, sans-serif;
            font-size: 12px;
            line-height: 1.4;
            color: #333;
        }}
        .header {{
            margin-bottom: 20px;
            border-bottom: 2px solid #4CAF50;
            padding-bottom: 15px;
            overflow: hidden;
        }}
        .company-info {{
            float: left;
            width: 50%;
        }}
        .invoice-info {{
            float: right;
            width: 45%;
            text-align: right;
        }}
        .clear {{ clear: both; }}
        .customer-info {{
            margin: 20px 0;
            padding: 10px;
            background-color: #f9f9f9;
            border-radius: 4px;
        }}
        table {{
            width: 100%;
            border-collapse: collapse;
            margin: 15px 0;
        }}
        th {{
            background-color: #4CAF50;
            color: white;
            padding: 8px;
            text-align: left;
            font-size: 11px;
        }}
        td {{
            padding: 6px 8px;
            border-bottom: 1px solid #ddd;
            font-size: 11px;
        }}
        .text-right {{ text-align: right; }}
        .totals {{
            margin-top: 15px;
            width: 300px;
            float: right;
        }}
        .totals table {{
            width: 100%;
        }}
        .totals td {{
            padding: 4px;
            border: none;
        }}
        .grand-total {{
            font-size: 14px;
            font-weight: bold;
            border-top: 2px solid #333;
        }}
        .footer {{
            margin-top: 40px;
            text-align: center;
            font-size: 10px;
            color: #999;
            border-top: 1px solid #ddd;
            padding-top: 15px;
        }}
        .status {{
            display: inline-block;
            padding: 2px 8px;
            border-radius: 3px;
            font-size: 10px;
            font-weight: bold;
            color: white;
        }}
        .status-paid {{ background-color: #4CAF50; }}
        .status-pending {{ background-color: #FF9800; }}
        .status-overdue {{ background-color: #F44336; }}
        .status-draft {{ background-color: #9E9E9E; }}
        h2 {{ margin: 0 0 5px 0; font-size: 18px; }}
        h3 {{ margin: 0 0 5px 0; font-size: 14px; }}
        p {{ margin: 2px 0; }}
    </style>
</head>
<body>
    <div class="header">
        <div class="company-info">
            <h2>{invoice.get('company_name', 'InvoiceApp')}</h2>
            <p>{invoice.get('company_address', '')}</p>
            <p>{invoice.get('company_phone', '')}</p>
            <p>{invoice.get('company_email', '')}</p>
        </div>
        <div class="invoice-info">
            <h2>INVOICE</h2>
            <p><strong>Invoice #:</strong> {invoice.get('invoice_number', '')}</p>
            <p><strong>Date:</strong> {format_date(invoice.get('invoice_date', ''))}</p>
            <p><strong>Due Date:</strong> {format_date(invoice.get('due_date', ''))}</p>
            <p><strong>Status:</strong> <span class="status status-{invoice.get('status', 'pending')}">{invoice.get('status', 'PENDING').upper()}</span></p>
        </div>
        <div class="clear"></div>
    </div>
    
    <div class="customer-info">
        <strong>Bill To:</strong><br>
        {invoice.get('customer_name', '')}<br>
        {invoice.get('customer_address', '')}<br>
        {invoice.get('customer_city', '')} {invoice.get('customer_state', '')} {invoice.get('customer_postal_code', '')}<br>
        {invoice.get('customer_country', '')}<br>
        <strong>Email:</strong> {invoice.get('customer_email', '')}<br>
        <strong>Phone:</strong> {invoice.get('customer_phone', '')}
    </div>
    
    <table>
        <thead>
            <tr>
                <th>Item</th>
                <th>Description</th>
                <th class="text-right">Qty</th>
                <th class="text-right">Unit Price</th>
                <th class="text-right">Discount</th>
                <th class="text-right">Total</th>
            </tr>
        </thead>
        <tbody>
"""
    
    for item in items:
        html += f"""
            <tr>
                <td><strong>{item.get('item_name', '')}</strong></td>
                <td>{item.get('description', '') or '-'}</td>
                <td class="text-right">{item.get('quantity', 0)}</td>
                <td class="text-right">{format_currency(item.get('unit_price', 0))}</td>
                <td class="text-right">{format_currency(item.get('discount_amount', 0))}</td>
                <td class="text-right">{format_currency(item.get('total_amount', 0))}</td>
            </tr>
"""
    
    subtotal = invoice.get('subtotal', 0)
    tax_amount = invoice.get('tax_amount', 0)
    discount_amount = invoice.get('discount_amount', 0)
    shipping_fee = invoice.get('shipping_fee', 0)
    total_amount = invoice.get('total_amount', 0)
    amount_paid = invoice.get('amount_paid', 0)
    balance_due = invoice.get('balance_due', 0)
    
    html += f"""
        </tbody>
    </table>
    
    <div class="totals">
        <table>
            <tr><td>Subtotal:</td><td class="text-right">{format_currency(subtotal)}</td></tr>
            <tr><td>Tax ({invoice.get('tax_rate', 0)}%):</td><td class="text-right">{format_currency(tax_amount)}</td></tr>
            <tr><td>Discount:</td><td class="text-right">-{format_currency(discount_amount)}</td></tr>
            <tr><td>Shipping:</td><td class="text-right">{format_currency(shipping_fee)}</td></tr>
            <tr class="grand-total"><td><strong>Total:</strong></td><td class="text-right"><strong>{format_currency(total_amount)}</strong></td></tr>
            <tr><td>Amount Paid:</td><td class="text-right">{format_currency(amount_paid)}</td></tr>
            <tr><td><strong>Balance Due:</strong></td><td class="text-right"><strong>{format_currency(balance_due)}</strong></td></tr>
        </table>
    </div>
    
    <div class="clear"></div>
    
    {f'<div style="margin-top: 20px;"><strong>Notes:</strong><br>{invoice.get("notes", "")}</div>' if invoice.get('notes') else ''}
    
    <div style="margin-top: 15px; font-size: 10px;">
        <strong>Terms & Conditions:</strong><br>
        {invoice.get('terms_conditions_default', 'Payment is due within 30 days.')}
    </div>
    
    <div class="footer">
        {invoice.get('footer_text_default', 'Thank you for your business!')}
    </div>
</body>
</html>"""
    
    return html

@app.route('/api/invoices/<int:invoice_id>/pdf', methods=['GET'])
@login_required
def generate_invoice_pdf(invoice_id):
    """Generate PDF for an invoice using pdfkit"""
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
                "description": row[2],
                "quantity": float(row[3]) if row[3] else 0,
                "unit_price": float(row[4]) if row[4] else 0,
                "discount_amount": float(row[5]) if row[5] else 0,
                "tax_rate": float(row[6]) if row[6] else 0,
                "tax_amount": float(row[7]) if row[7] else 0,
                "total_amount": float(row[8]) if row[8] else 0
            })
        
        html_content = create_invoice_pdf_html(invoice, items)
        
        conn.close()
        conn = None
        
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
        
        pdf_data = pdfkit.from_string(html_content, False, configuration=pdfkit_config, options=options)
        
        response = make_response(pdf_data)
        response.headers['Content-Type'] = 'application/pdf'
        response.headers['Content-Disposition'] = f'attachment; filename=invoice_{invoice["invoice_number"]}.pdf'
        
        return response
        
    except Exception as e:
        if conn:
            conn.close()
        print(f"Error generating PDF: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": f"PDF generation failed: {str(e)}"}), 500

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

        return jsonify({
            "start_date": start_date,
            "end_date": end_date,
            "total_revenue": total_revenue,
            "total_expenses": total_expenses,
            "net_profit": total_revenue - total_expenses,
            "revenue_by_month": revenue_by_month,
            "expenses_by_category": expenses_by_category
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
    
    app.run(debug=True, host='0.0.0.0', port=5000)
