from playwright.sync_api import sync_playwright
import time

def test_page(page, menu_name, screenshot_name):
    try:
        menu = page.locator(f'.el-menu-item:has-text("{menu_name}")')
        if menu.count() > 0:
            print(f"点击: {menu_name}")
            menu.first.click()
            time.sleep(2)
            
            url = page.url
            print(f"  URL: {url}")
            
            page.screenshot(path=f'D:/software/Idea_Project/boss_01_gbs_commerce_system/supermarket-cashier/test/screenshots/{screenshot_name}.png', full_page=True)
            
            if '404' in page.content() or 'Not Found' in page.title():
                print(f"  结果: 404错误")
                return "404"
            else:
                print(f"  结果: 正常")
                return "OK"
        else:
            print(f"未找到菜单: {menu_name}")
            return "NOT_FOUND"
    except Exception as e:
        print(f"  错误: {e}")
        return "ERROR"

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False)
    context = browser.new_context(ignore_https_errors=True)
    page = context.new_page()
    
    print("登录...")
    page.goto('https://192.168.3.180:5173/')
    page.wait_for_load_state('networkidle')
    page.fill('input[placeholder="请输入用户名"]', 'admin')
    page.fill('input[placeholder="请输入密码"]', 'admin123')
    page.click('button:has-text("登录")')
    time.sleep(3)
    
    print("\n=== 开始测试各菜单 ===\n")
    
    results = {}
    
    results['首页'] = test_page(page, '首页', 'test_01_home')
    results['开始收银'] = test_page(page, '开始收银', 'test_02_cashier')
    results['会员管理'] = test_page(page, '会员管理', 'test_03_member')
    results['智慧收银'] = test_page(page, '智慧收银', 'test_04_smart')
    results['商品列表'] = test_page(page, '商品列表', 'test_05_product')
    results['订单查询'] = test_page(page, '订单查询', 'test_06_order')
    
    print("\n=== 测试结果汇总 ===")
    for menu, status in results.items():
        status_text = "✅ 正常" if status == "OK" else "❌ " + status
        print(f"{menu}: {status_text}")
    
    print("\n测试完成")
    time.sleep(3)
    browser.close()