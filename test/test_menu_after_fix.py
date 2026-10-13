from playwright.sync_api import sync_playwright
import time

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False)
    context = browser.new_context(ignore_https_errors=True)
    page = context.new_page()
    
    print("1. 访问登录页并清除缓存...")
    page.goto('https://192.168.3.180:5173/')
    page.evaluate('localStorage.clear()')
    page.reload()
    page.wait_for_load_state('networkidle')
    time.sleep(1)
    
    print("2. 登录...")
    page.fill('input[placeholder="请输入用户名"]', 'admin')
    page.fill('input[placeholder="请输入密码"]', 'admin123')
    page.click('button:has-text("登录")')
    time.sleep(3)
    
    page.screenshot(path='D:/software/Idea_Project/boss_01_gbs_commerce_system/supermarket-cashier/test/screenshots/after_fix.png', full_page=True)
    
    print(f"当前URL: {page.url}")
    print(f"页面标题: {page.title()}")
    
    menu_items = page.locator('.el-menu-item').all()
    print(f"发现 {len(menu_items)} 个菜单项")
    
    print("\n菜单列表:")
    for i, item in enumerate(menu_items):
        try:
            text = item.inner_text()
            print(f"  {i+1}. {text}")
        except:
            pass
    
    print("\n测试点击几个关键菜单...")
    
    test_menus = ['收银台', '订单查询', '商品列表', '商品入库']
    for menu_name in test_menus:
        try:
            menu = page.locator(f'.el-menu-item:has-text("{menu_name}")')
            if menu.count() > 0:
                print(f"\n点击: {menu_name}")
                menu.first.click()
                time.sleep(2)
                url = page.url
                print(f"  URL: {url}")
                if '404' in page.content():
                    print(f"  结果: 404错误")
                else:
                    print(f"  结果: 正常")
            else:
                print(f"\n未找到菜单: {menu_name}")
        except Exception as e:
            print(f"\n点击 {menu_name} 失败: {e}")
    
    print("\n测试完成")
    time.sleep(5)
    browser.close()