"""Check available Gemini models"""
import google.generativeai as genai
from dotenv import load_dotenv
import os

load_dotenv()

genai.configure(api_key=os.getenv("GEMINI_API_KEY"))

print("Available Gemini models:\n")
for model in genai.list_models():
    if 'generateContent' in model.supported_generation_methods:
        print(f"  • {model.name}")
        print(f"    Display name: {model.display_name}")
        print(f"    Methods: {model.supported_generation_methods}")
        print()
