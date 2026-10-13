from playwright.sync_api import sync_playwright
import time

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False)
    context = browser.new_context(ignore_https_errors=True)
    page = context.new_page()
    
    print("1. 访问登录页...")
    page.goto('https://192.168.3.180:5173/')
    page.wait_for_load_state('networkidle')
    time.sleep(2)
    page.screenshot(path='D:/software/Idea_Project/boss_01_gbs_commerce_system/supermarket-cashier/test/screenshots/01_login.png', full_page=True)
    
    print("2. 尝试登录...")
    page.fill('input[placeholder="请输入用户名"]', 'admin')
    page.fill('input[placeholder="请输入密码"]', 'admin123')
    page.click('button:has-text("登录")')
    time.sleep(3)
    
    page.screenshot(path='D:/software/Idea_Project/boss_01_gbs_commerce_system/supermarket-cashier/test/screenshots/02_after_login.png', full_page=True)
    print("登录后截图完成")
    
    print(f"当前URL: {page.url}")
    print(f"页面标题: {page.title()}")
    
    menu_items = page.locator('.el-menu-item, .el-sub-menu').all()
    print(f"发现 {len(menu_items)} 个菜单项")
    
    for i, item in enumerate(menu_items):
        try:
            text = item.inner_text()
            print(f"菜单 {i+1}: {text}")
        except:
            pass
    
    if len(menu_items) > 0:
        print("\n3. 测试点击第一个菜单...")
        first_menu = menu_items[0]
        menu_text = first_menu.inner_text()
        print(f"点击菜单: {menu_text}")
        first_menu.click()
        time.sleep(2)
        page.screenshot(path='D:/software/Idea_Project/boss_01_gbs_commerce_system/supermarket-cashier/test/screenshots/03_menu_click.png', full_page=True)
        
        print(f"点击后URL: {page.url}")
    
    print("\n测试完成，浏览器将保持打开5秒...")
    time.sleep(5)
    browser.close()