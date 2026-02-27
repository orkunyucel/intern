#!/usr/bin/env python3
"""
Flowable Auto-Deploy Script

Automatically deploys BPMN processes to Flowable via REST API.
Also creates user groups and test data.
"""
import requests
import os
import sys
from pathlib import Path

# Flowable Configuration
FLOWABLE_URL = os.getenv("FLOWABLE_URL", "http://localhost:8080")
FLOWABLE_USER = os.getenv("FLOWABLE_USER", "admin")
FLOWABLE_PASS = os.getenv("FLOWABLE_PASS", "test")

# API Endpoints (Flowable Task App API)
DEPLOYMENT_API = f"{FLOWABLE_URL}/flowable-task/process-api/repository/deployments"
PROCESS_API = f"{FLOWABLE_URL}/flowable-task/process-api/repository/process-definitions"
RUNTIME_API = f"{FLOWABLE_URL}/flowable-task/process-api/runtime/process-instances"
GROUPS_API = f"{FLOWABLE_URL}/flowable-idm/api/idm-api/groups"
USERS_API = f"{FLOWABLE_URL}/flowable-idm/api/idm-api/users"
ENGINE_API = f"{FLOWABLE_URL}/flowable-task/process-api/management/engine"

AUTH = (FLOWABLE_USER, FLOWABLE_PASS)
HEADERS = {"Accept": "application/json"}


def check_flowable_connection():
    """Check if Flowable is running"""
    print("🔍 Checking Flowable connection...")
    try:
        resp = requests.get(ENGINE_API, auth=AUTH, timeout=10)
        if resp.status_code == 200:
            print("✅ Flowable is running!")
            return True
        else:
            print(f"❌ Flowable returned status {resp.status_code}")
            return False
    except requests.exceptions.ConnectionError:
        print("❌ Cannot connect to Flowable. Make sure it's running on port 8080")
        return False


def deploy_bpmn(bpmn_file: str, deployment_name: str = None):
    """Deploy a BPMN file to Flowable"""
    file_path = Path(bpmn_file)
    
    if not file_path.exists():
        print(f"❌ File not found: {bpmn_file}")
        return None
    
    deployment_name = deployment_name or file_path.stem
    
    print(f"\n📦 Deploying: {file_path.name}")
    print(f"   Deployment name: {deployment_name}")
    
    with open(file_path, 'rb') as f:
        files = {
            'file': (file_path.name, f, 'application/xml')
        }
        data = {
            'deploymentName': deployment_name,
            'tenantId': ''
        }
        
        resp = requests.post(
            DEPLOYMENT_API,
            files=files,
            data=data,
            auth=AUTH
        )
    
    if resp.status_code in [200, 201]:
        result = resp.json()
        print(f"✅ Deployed successfully!")
        print(f"   Deployment ID: {result.get('id')}")
        print(f"   Name: {result.get('name')}")
        return result
    else:
        print(f"❌ Deployment failed: {resp.status_code}")
        print(f"   Response: {resp.text[:500]}")
        return None


def list_process_definitions():
    """List all deployed process definitions"""
    print("\n📋 Listing Process Definitions...")
    
    resp = requests.get(PROCESS_API, auth=AUTH, headers=HEADERS)
    
    if resp.status_code == 200:
        data = resp.json()
        processes = data.get('data', [])
        
        if processes:
            print(f"\n   Found {len(processes)} process(es):")
            for proc in processes:
                print(f"   - {proc['name']} (key: {proc['key']}, version: {proc['version']})")
        else:
            print("   No processes deployed yet.")
        
        return processes
    else:
        print(f"❌ Failed to list processes: {resp.status_code}")
        return []


def create_groups():
    """Create required user groups"""
    groups = [
        {"id": "emergency-team", "name": "Emergency Team", "type": "security-role"},
        {"id": "managers", "name": "Managers", "type": "security-role"},
        {"id": "support-team", "name": "Support Team", "type": "security-role"},
    ]
    
    print("\n👥 Creating user groups...")
    
    for group in groups:
        resp = requests.post(GROUPS_API, json=group, auth=AUTH, headers={"Content-Type": "application/json"})
        
        if resp.status_code in [200, 201]:
            print(f"   ✅ Created group: {group['name']}")
        elif resp.status_code == 409:
            print(f"   ⏭️  Group already exists: {group['name']}")
        else:
            print(f"   ❌ Failed to create group {group['name']}: {resp.status_code}")


def add_user_to_group(user_id: str, group_id: str):
    """Add user to a group"""
    resp = requests.post(
        f"{GROUPS_API}/{group_id}/members",
        json={"userId": user_id},
        auth=AUTH,
        headers={"Content-Type": "application/json"}
    )
    return resp.status_code in [200, 201, 409]


def setup_admin_groups():
    """Add admin user to all groups"""
    print("\n🔑 Adding admin to all groups...")
    
    groups = ["emergency-team", "managers", "support-team"]
    for group in groups:
        if add_user_to_group("admin", group):
            print(f"   ✅ Admin added to {group}")
        else:
            print(f"   ⏭️  Admin already in {group}")


def start_test_process(process_key: str, variables: dict):
    """Start a process instance for testing"""
    print(f"\n🚀 Starting test process: {process_key}")
    
    payload = {
        "processDefinitionKey": process_key,
        "variables": [
            {"name": k, "value": v} for k, v in variables.items()
        ]
    }
    
    resp = requests.post(
        RUNTIME_API,
        json=payload,
        auth=AUTH,
        headers={"Content-Type": "application/json"}
    )
    
    if resp.status_code in [200, 201]:
        result = resp.json()
        print(f"✅ Process started!")
        print(f"   Instance ID: {result.get('id')}")
        print(f"   Business Key: {result.get('businessKey')}")
        return result
    else:
        print(f"❌ Failed to start process: {resp.status_code}")
        print(f"   Response: {resp.text[:500]}")
        return None


def main():
    print("=" * 60)
    print("🚀 Flowable Auto-Deploy Script")
    print("=" * 60)
    
    # 1. Check connection
    if not check_flowable_connection():
        print("\n⚠️  Please start Flowable first:")
        print("   docker-compose up -d flowable")
        sys.exit(1)
    
    # 2. Create groups
    create_groups()
    setup_admin_groups()
    
    # 3. Deploy BPMN files
    script_dir = Path(__file__).parent
    bpmn_files = list(script_dir.glob("*.bpmn20.xml"))
    
    if not bpmn_files:
        print("\n⚠️  No BPMN files found in flowable/ directory")
    else:
        for bpmn_file in bpmn_files:
            deploy_bpmn(str(bpmn_file))
    
    # 4. List deployed processes
    processes = list_process_definitions()
    
    # 5. Start a test process
    if processes:
        print("\n" + "=" * 60)
        print("🎉 DEPLOYMENT COMPLETE!")
        print("=" * 60)
        print("\n📍 Access Flowable UI:")
        print(f"   Modeler: {FLOWABLE_URL}/flowable-modeler")
        print(f"   Task App: {FLOWABLE_URL}/flowable-task")
        print(f"   Admin: {FLOWABLE_URL}/flowable-admin")
        print("\n📋 Login: admin / test")
        print("\n🧪 To start a test process manually:")
        print(f'''
   curl -X POST {RUNTIME_API} \\
     -u admin:test \\
     -H "Content-Type: application/json" \\
     -d '{{
       "processDefinitionKey": "advancedIntakeProcess",
       "variables": [
         {{"name": "question", "value": "BPM politikasında izin süreci nasıl işler?"}},
         {{"name": "customerId", "value": "CUST-001"}},
         {{"name": "source", "value": "text"}},
         {{"name": "agentType", "value": "auto"}}
       ]
     }}'
''')


if __name__ == "__main__":
    main()
