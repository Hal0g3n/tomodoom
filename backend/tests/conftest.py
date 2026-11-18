import os
import sys

# The backend is a flat folder of modules (PythonAnywhere imports them from its directory).
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
