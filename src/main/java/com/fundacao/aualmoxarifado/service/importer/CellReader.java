package com.fundacao.aualmoxarifado.service.importer;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Lê células de uma planilha tolerando os tipos variados que o Apache POI devolve.
 *
 * <p>Centraliza a chatice de NUMERIC vs STRING vs FORMULA, valores nulos
 * e a normalização de strings.</p>
 */
public final class CellReader {

    private CellReader() {}

    public static String string(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING  -> trimToNull(cell.getStringCellValue());
            case NUMERIC -> trimToNull(formatNumeric(cell));
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case FORMULA -> stringFromFormula(cell);
            case BLANK, ERROR, _NONE -> null;
        };
    }

    public static Integer integer(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return (int) cell.getNumericCellValue();
        }
        String s = string(row, col);
        if (s == null) return null;
        try { return Integer.parseInt(s.replaceAll("[^0-9-]", "")); }
        catch (NumberFormatException e) { return null; }
    }

    public static BigDecimal decimal(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue())
                    .setScale(2, RoundingMode.HALF_UP);
        }
        String s = string(row, col);
        if (s == null) return null;
        try {
            return new BigDecimal(s.replace("R$", "").replace(".", "").replace(",", ".").trim())
                    .setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String formatNumeric(Cell cell) {
        double v = cell.getNumericCellValue();
        if (v == Math.floor(v)) return Long.toString((long) v);
        return Double.toString(v);
    }

    private static String stringFromFormula(Cell cell) {
        try { return trimToNull(cell.getStringCellValue()); }
        catch (Exception e) {
            try { return trimToNull(formatNumeric(cell)); }
            catch (Exception ex) { return null; }
        }
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
