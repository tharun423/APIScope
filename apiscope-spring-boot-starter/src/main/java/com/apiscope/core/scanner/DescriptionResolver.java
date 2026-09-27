package com.apiscope.core.scanner;

import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

@Component
public class DescriptionResolver {

    public String resolveBusinessLogic(Method method) {
        try {
            String sourceFile = findSourceFile(method.getDeclaringClass());
            if (sourceFile == null) return null;
            String src = java.nio.file.Files.readString(java.nio.file.Path.of(sourceFile));
            String methodName = method.getName();

            StringBuilder comments = new StringBuilder();

            String classDoc = extractJavadocBefore(src,
                    "(?:public\\s+)?(?:class|interface)\\s+" + method.getDeclaringClass().getSimpleName());
            if (classDoc != null) comments.append(classDoc);

            String methodDoc = extractJavadocBefore(src,
                    "(?:public|protected|private)[^{]*\\b" + methodName + "\\s*\\(");
            if (methodDoc != null) {
                if (!comments.isEmpty()) comments.append(" ");
                comments.append(methodDoc);
            }

            String inline = extractInlineComments(src, methodName);
            if (inline != null && !inline.isBlank()) {
                if (!comments.isEmpty()) comments.append(" ");
                comments.append(inline);
            }

            return comments.isEmpty() ? null : comments.toString().trim();
        } catch (Exception e) {
            return null;
        }
    }

    private String findSourceFile(Class<?> clazz) {
        String classPath = clazz.getName().replace('.', java.io.File.separatorChar) + ".java";
        for (String root : List.of("src/main/java", "../apiscope-sample-app/src/main/java",
                                   "../../apiscope-sample-app/src/main/java")) {
            java.io.File f = new java.io.File(root, classPath);
            if (f.exists()) return f.getAbsolutePath();
        }
        try {
            String fileName = clazz.getSimpleName() + ".java";
            java.nio.file.Path start = java.nio.file.Path.of(System.getProperty("user.dir"));
            try (var stream = java.nio.file.Files.walk(start, 10)) {
                return stream
                        .filter(p -> p.getFileName().toString().equals(fileName))
                        .map(java.nio.file.Path::toString)
                        .findFirst().orElse(null);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String extractJavadocBefore(String src, String anchorRegex) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "/\\*\\*([\\s\\S]*?)\\*/\\s*(?:@\\w[^\n]*\n\\s*)*" + anchorRegex,
                java.util.regex.Pattern.MULTILINE);
        java.util.regex.Matcher m = p.matcher(src);
        String last = null;
        while (m.find()) last = m.group(1);
        return last != null ? cleanComment(last) : null;
    }

    private String extractInlineComments(String src, String methodName) {
        java.util.regex.Pattern sig = java.util.regex.Pattern.compile(
                "(?:public|protected|private)[^{]*\\b" + methodName + "\\s*\\([^)]*\\)[^{]*\\{");
        java.util.regex.Matcher ms = sig.matcher(src);
        if (!ms.find()) return null;

        int depth = 1, i = ms.end();
        while (i < src.length() && depth > 0) {
            char c = src.charAt(i);
            if (c == '{') depth++; else if (c == '}') depth--;
            i++;
        }
        String body = src.substring(ms.end(), i - 1);

        StringBuilder sb = new StringBuilder();
        java.util.regex.Matcher lc = java.util.regex.Pattern.compile("//\\s*(.+)").matcher(body);
        while (lc.find()) {
            String c = lc.group(1).trim();
            if (!c.isBlank()) sb.append(c).append(". ");
        }
        return sb.toString().trim();
    }

    private String cleanComment(String raw) {
        return raw
                .replaceAll("\n\\s*\\*+", " ")
                .replaceAll("@param\\s+\\S+", "")
                .replaceAll("@return", "Returns:")
                .replaceAll("@throws\\s+\\S+", "")
                .replaceAll("\\{@[^}]+}", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }
}
        try {
            String sourceFile = findSourceFile(method.getDeclaringClass());
            if (sourceFile == null) return null;
            String src = java.nio.file.Files.readString(java.nio.file.Path.of(sourceFile));
            String methodName = method.getName();

            StringBuilder comments = new StringBuilder();

            String classDoc = extractJavadocBefore(src,
                    "(?:public\\s+)?(?:class|interface)\\s+" + method.getDeclaringClass().getSimpleName());
            if (classDoc != null) comments.append(classDoc);

            String methodDoc = extractJavadocBefore(src,
                    "(?:public|protected|private)[^{]*\\b" + methodName + "\\s*\\(");
            if (methodDoc != null) {
                if (!comments.isEmpty()) comments.append(" ");
                comments.append(methodDoc);
            }

            String inline = extractInlineComments(src, methodName);
            if (inline != null && !inline.isBlank()) {
                if (!comments.isEmpty()) comments.append(" ");
                comments.append(inline);
            }

            return comments.isEmpty() ? null : comments.toString().trim();
        } catch (Exception e) {
            return null;
        }
    }

    // ── Source file lookup ────────────────────────────────────────────────────

    private String findSourceFile(Class<?> clazz) {
        String classPath = clazz.getName().replace('.', java.io.File.separatorChar) + ".java";
        for (String root : List.of("src/main/java", "../apiscope-sample-app/src/main/java",
                                   "../../apiscope-sample-app/src/main/java")) {
            java.io.File f = new java.io.File(root, classPath);
            if (f.exists()) return f.getAbsolutePath();
        }
        try {
            String fileName = clazz.getSimpleName() + ".java";
            java.nio.file.Path start = java.nio.file.Path.of(System.getProperty("user.dir"));
            try (var stream = java.nio.file.Files.walk(start, 10)) {
                return stream
                        .filter(p -> p.getFileName().toString().equals(fileName))
                        .map(java.nio.file.Path::toString)
                        .findFirst().orElse(null);
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ── Comment extractors ────────────────────────────────────────────────────

    private String extractJavadocBefore(String src, String anchorRegex) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "/\\*\\*([\\s\\S]*?)\\*/\\s*(?:@\\w[^\n]*\n\\s*)*" + anchorRegex,
                java.util.regex.Pattern.MULTILINE);
        java.util.regex.Matcher m = p.matcher(src);
        String last = null;
        while (m.find()) last = m.group(1);
        return last != null ? cleanComment(last) : null;
    }

    private String extractInlineComments(String src, String methodName) {
        java.util.regex.Pattern sig = java.util.regex.Pattern.compile(
                "(?:public|protected|private)[^{]*\\b" + methodName + "\\s*\\([^)]*\\)[^{]*\\{");
        java.util.regex.Matcher ms = sig.matcher(src);
        if (!ms.find()) return null;

        int depth = 1, i = ms.end();
        while (i < src.length() && depth > 0) {
            char c = src.charAt(i);
            if (c == '{') depth++; else if (c == '}') depth--;
            i++;
        }
        String body = src.substring(ms.end(), i - 1);

        StringBuilder sb = new StringBuilder();
        java.util.regex.Matcher lc = java.util.regex.Pattern.compile("//\\s*(.+)").matcher(body);
        while (lc.find()) {
            String c = lc.group(1).trim();
            if (!c.isBlank()) sb.append(c).append(". ");
        }
        return sb.toString().trim();
    }

    private String cleanComment(String raw) {
        return raw
                .replaceAll("\n\\s*\\*+", " ")
                .replaceAll("@param\\s+\\S+", "")
                .replaceAll("@return", "Returns:")
                .replaceAll("@throws\\s+\\S+", "")
                .replaceAll("\\{@[^}]+}", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private static String camelToSentence(String name) {
        if (name == null || name.isBlank()) return name;
        String spaced = name.replaceAll("([A-Z])", " $1").trim();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
