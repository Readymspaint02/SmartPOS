from playwright.sync_api import sync_playwright
import time

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    context = browser.new_context(ignore_https_errors=True)
    page = context.new_page()
    
    print("正在访问页面...")
    page.goto('https://192.168.3.180:5173/dashboard')
    page.wait_for_load_state('networkidle')
    time.sleep(2)
    
    page.screenshot(path='D:/software/Idea_Project/boss_01_gbs_commerce_system/supermarket-cashier/test/screenshots/dashboard.png', full_page=True)
    print("截图已保存")
    
    print(f"页面标题: {page.title()}")
    
    links = page.locator('a').all()
    buttons = page.locator('button').all()
    print(f"发现 {len(links)} 个链接")
    print(f"发现 {len(buttons)} 个按钮")
    
    menu_items = page.locator('.el-menu-item, .el-sub-menu').all()
    print(f"发现 {len(menu_items)} 个菜单项")
    
    for i, item in enumerate(menu_items):
        try:
            text = item.inner_text()
            print(f"菜单 {i+1}: {text}")
        except:
            pass
    
    browser.close()
    print("测试完成")