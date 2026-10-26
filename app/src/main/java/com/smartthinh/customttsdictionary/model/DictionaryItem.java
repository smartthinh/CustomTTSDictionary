package com.smartthinh.customttsdictionary.model;

public class DictionaryItem {
    private String originalWord;
    private String replacementWord;
    private int caseSensitive = 0; // 0 = khong phan biet hoa thuong, 1 = co phan biet
    private int isRegex = 0;        // 0 = tu thuong, 1 = bieu thuc chinh quy

    public DictionaryItem(String originalWord, String replacementWord) {
        this.originalWord = originalWord != null ? originalWord.trim() : "";
        this.replacementWord = replacementWord != null ? replacementWord : "";
        this.caseSensitive = 0;
        this.isRegex = 0;
    }

    public DictionaryItem(String originalWord, String replacementWord, int caseSensitive, int isRegex) {
        this.originalWord = originalWord != null ? originalWord.trim() : "";
        this.replacementWord = replacementWord != null ? replacementWord : "";
        this.caseSensitive = caseSensitive;
        this.isRegex = isRegex;
    }

    public String getOriginalWord() {
        return originalWord;
    }

    public void setOriginalWord(String originalWord) {
        this.originalWord = originalWord != null ? originalWord.trim() : "";
    }

    public String getReplacementWord() {
        return replacementWord;
    }

    public void setReplacementWord(String replacementWord) {
        this.replacementWord = replacementWord != null ? replacementWord : "";
    }

    public int getCaseSensitive() {
        return caseSensitive;
    }

    public int getIsRegex() {
        return isRegex;
    }

    /**
     * Dinh dang giong het add-on zhcnViDict:
     * <tu_goc>\t<tu_thay_the>\t0\t0
     */
    public String toDicLine() {
        return originalWord + "\t" + replacementWord + "\t" + caseSensitive + "\t" + isRegex;
    }

    public static DictionaryItem fromDicLine(String line) {
        if (line == null) return null;
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }

        String[] parts = line.split("\t");
        if (parts.length >= 2) {
            String orig = parts[0].trim();
            if (orig.startsWith("\uFEFF")) {
                orig = orig.substring(1).trim();
            }
            String repl = parts[1].trim();

            // CLEANUP: Xu ly '的' (đích) va '了' (liễu) theo chuan zhcnViDict
            if (orig.equals("的")) {
                repl = "";
            } else if (orig.endsWith("的")) {
                if (repl.endsWith(" đích")) {
                    repl = repl.substring(0, repl.length() - 5);
                } else if (repl.endsWith("đích")) {
                    repl = repl.substring(0, repl.length() - 4);
                }
            }

            if (orig.equals("了")) {
                repl = "";
            } else if (orig.endsWith("了")) {
                if (repl.endsWith(" liễu")) {
                    repl = repl.substring(0, repl.length() - 5);
                } else if (repl.endsWith("liễu")) {
                    repl = repl.substring(0, repl.length() - 4);
                }
            }

            int cs = 0;
            int reg = 0;
            if (parts.length >= 3) {
                try { cs = Integer.parseInt(parts[2].trim()); } catch (Exception ignored) {}
            }
            if (parts.length >= 4) {
                try { reg = Integer.parseInt(parts[3].trim()); } catch (Exception ignored) {}
            }
            if (!orig.isEmpty()) {
                return new DictionaryItem(orig, repl, cs, reg);
            }
        }
        return null;
    }
}
