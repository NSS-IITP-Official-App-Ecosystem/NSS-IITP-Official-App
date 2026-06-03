function doPost(e) {
  try {
    var payload = JSON.parse(e.postData.contents);
    var sheets = payload.sheets; // Map of sheetName -> 2D array of values
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    
    // Process each sheet provided in the payload
    for (var sheetName in sheets) {
      var sheet = ss.getSheetByName(sheetName);
      if (!sheet) {
        sheet = ss.insertSheet(sheetName);
      }
      sheet.clear();
      
      var data = sheets[sheetName];
      if (data && data.length > 0) {
        // Set values in sheet
        sheet.getRange(1, 1, data.length, data[0].length).setValues(data);
        
        // Basic Formatting
        var headerRange = sheet.getRange(1, 1, 1, data[0].length);
        headerRange.setFontWeight("bold");
        headerRange.setBackground("#D3D3D3"); // Light grey background
        
        // Auto-fit columns
        sheet.autoResizeColumns(1, data[0].length);
      }
    }
    
    return ContentService.createTextOutput(JSON.stringify({ success: true }))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ success: false, error: err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}
