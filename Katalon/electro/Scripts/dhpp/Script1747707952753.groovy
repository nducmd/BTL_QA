import static com.kms.katalon.core.checkpoint.CheckpointFactory.findCheckpoint
import static com.kms.katalon.core.testcase.TestCaseFactory.findTestCase
import static com.kms.katalon.core.testdata.TestDataFactory.findTestData
import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject
import static com.kms.katalon.core.testobject.ObjectRepository.findWindowsObject
import com.kms.katalon.core.checkpoint.Checkpoint as Checkpoint
import com.kms.katalon.core.cucumber.keyword.CucumberBuiltinKeywords as CucumberKW
import com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords as Mobile
import com.kms.katalon.core.model.FailureHandling as FailureHandling
import com.kms.katalon.core.testcase.TestCase as TestCase
import com.kms.katalon.core.testdata.TestData as TestData
import com.kms.katalon.core.testng.keyword.TestNGBuiltinKeywords as TestNGKW
import com.kms.katalon.core.testobject.TestObject as TestObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI
import com.kms.katalon.core.windows.keyword.WindowsBuiltinKeywords as Windows
import internal.GlobalVariable as GlobalVariable
import org.openqa.selenium.Keys as Keys

WebUI.openBrowser('')

WebUI.navigateToUrl('http://localhost:3000/signin')

WebUI.setText(findTestObject('Object Repository/dhpp/Page_ng nhp  Electro/input_Tn ti khon_mantine-3ftbk89k9'), 'customer1')

WebUI.setEncryptedText(findTestObject('Object Repository/dhpp/Page_ng nhp  Electro/input_Mt khu_mantine-roww0y0my'), 'HeCM15nHKBI=')

WebUI.click(findTestObject('Object Repository/dhpp/Page_ng nhp  Electro/button_ng nhp'))

WebUI.click(findTestObject('Object Repository/dhpp/Page_Trang ch  Electro/span_Dell XPS 13 9315'))

WebUI.click(findTestObject('Object Repository/dhpp/Page_Dell XPS 13 9315  Electro/button_Chn mua'))

WebUI.click(findTestObject('Object Repository/dhpp/Page_Dell XPS 13 9315  Electro/svg_Danh mc sn phm_mantine-gnzaph mantine-G_855041'))

WebUI.click(findTestObject('Object Repository/dhpp/Page_Gi hng  Electro/div_PayPal'))

WebUI.click(findTestObject('Object Repository/dhpp/Page_Gi hng  Electro/button_t mua_1'))

WebUI.click(findTestObject('Object Repository/dhpp/Page_Gi hng  Electro/button_Xc nhn t mua_1'))

WebUI.verifyElementText(findTestObject('Object Repository/dhpp/Page_Gi hng  Electro/div_Hon tt thanh ton PayPal bng cch bm nt di'), 
    'Hoàn tất thanh toán PayPal bằng cách bấm nút dưới')

WebUI.verifyElementPresent(findTestObject('Object Repository/dhpp/Page_Gi hng  Electro/div_Hon tt thanh ton PayPal bng cch bm nt di'), 
    0)

WebUI.closeBrowser()

