package com.instser.chat.parser.impl;

import com.instser.chat.parser.DocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.text.DecimalFormat;

/**
 * Excel文档解析器
 * 支持.xls和.xlsx格式
 *
 * @author instser
 * @since 2025-06-27
 */
@Slf4j
public class ExcelDocumentParser implements DocumentParser {

    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#.##########");

    @Override
    public String parseDocument(byte[] fileBytes, String fileName, String fileType) throws Exception {
        StringBuilder content = new StringBuilder();
        
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(fileBytes)) {
            Workbook workbook;
            
            if (fileType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
                // 处理.xlsx文件
                workbook = new XSSFWorkbook(inputStream);
                content.append("文档类型: Excel文档 (.xlsx)\n");
            } else if (fileType.equals("application/vnd.ms-excel")) {
                // 处理.xls文件
                workbook = new HSSFWorkbook(inputStream);
                content.append("文档类型: Excel文档 (.xls)\n");
            } else {
                throw new IllegalArgumentException("不支持的Excel文档格式: " + fileType);
            }
            
            try {
                content.append("工作表数量: ").append(workbook.getNumberOfSheets()).append("\n\n");
                
                // 遍历所有工作表
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    Sheet sheet = workbook.getSheetAt(i);
                    content.append(parseSheet(sheet, i + 1));
                }
            } finally {
                workbook.close();
            }
        }
        
        return content.toString().trim();
    }

    /**
     * 解析工作表内容
     */
    private String parseSheet(Sheet sheet, int sheetIndex) {
        StringBuilder sheetContent = new StringBuilder();
        
        String sheetName = sheet.getSheetName();
        sheetContent.append("[工作表").append(sheetIndex).append(": ").append(sheetName).append("]\n");
        
        int lastRowNum = sheet.getLastRowNum();
        if (lastRowNum < 0) {
            sheetContent.append("工作表为空\n\n");
            return sheetContent.toString();
        }
        
        // 限制最大行数，避免处理过大的文件
        int maxRows = Math.min(lastRowNum + 1, 1000);
        
        for (int rowIndex = 0; rowIndex < maxRows; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            
            StringBuilder rowContent = new StringBuilder();
            short lastCellNum = row.getLastCellNum();
            
            // 限制最大列数
            int maxCells = Math.min(lastCellNum, 50);
            boolean hasContent = false;
            
            for (int cellIndex = 0; cellIndex < maxCells; cellIndex++) {
                Cell cell = row.getCell(cellIndex);
                String cellValue = getCellValue(cell);
                
                if (!cellValue.trim().isEmpty()) {
                    hasContent = true;
                }
                
                rowContent.append(cellValue);
                if (cellIndex < maxCells - 1) {
                    rowContent.append(" | ");
                }
            }
            
            // 只添加有内容的行
            if (hasContent) {
                sheetContent.append(rowContent.toString().trim()).append("\n");
            }
        }
        
        if (lastRowNum >= 1000) {
            sheetContent.append("... (数据过多，仅显示前1000行)\n");
        }
        
        sheetContent.append("\n");
        return sheetContent.toString();
    }

    /**
     * 获取单元格值
     */
    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue().toString();
                    } else {
                        double numericValue = cell.getNumericCellValue();
                        // 如果是整数，不显示小数点
                        if (numericValue == Math.floor(numericValue)) {
                            return String.valueOf((long) numericValue);
                        } else {
                            return NUMBER_FORMAT.format(numericValue);
                        }
                    }
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    try {
                        // 尝试获取公式计算结果
                        return getCellValue(cell.getCachedFormulaResultType(), cell);
                    } catch (Exception e) {
                        return "公式: " + cell.getCellFormula();
                    }
                case BLANK:
                default:
                    return "";
            }
        } catch (Exception e) {
            log.warn("读取单元格值失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 获取公式单元格的计算结果
     */
    private String getCellValue(CellType cellType, Cell cell) {
        switch (cellType) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                double numericValue = cell.getNumericCellValue();
                if (numericValue == Math.floor(numericValue)) {
                    return String.valueOf((long) numericValue);
                } else {
                    return NUMBER_FORMAT.format(numericValue);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    @Override
    public String[] getSupportedTypes() {
        return new String[]{
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        };
    }
}
