/**
 * @author Mehmet Akif Balcı - akif.balci@ogr.sakarya.edu.tr - B211210075
 * @since 06.04.2024
 * <p>
 * <p>
 * Bu Java programı, kullanıcıdan bir GitHub deposu URL'si alır, belirtilen depoyu klonlar,
 * depodaki Java dosyalarını bulur ve bu dosyalardaki sınıfların ve fonksiyonların analizini yapar
 * </p>
 */


package pdpOdev;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) {
        String repoUrl = getRepoUrlFromUser();

        // Create folder name from the given URL
        String repoFolderName = generateRepoFolderName(repoUrl);

        // Clone the folder and find Java files
        List<String> javaFiles = cloneAndFindJavaFiles(repoUrl, repoFolderName);

        // Read found Java files and extract classes and functions
        Map<String, Map<String, Integer>> classAnalysis = extractClassAnalysis(javaFiles);

        // Print the extracted class analysis
        printClassAnalysis(classAnalysis);
    }

    private static String getRepoUrlFromUser() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("GitHub repo URL'sini girin: ");
        return scanner.nextLine();
    }

    private static String generateRepoFolderName(String repoUrl) {
        return repoUrl.substring(repoUrl.lastIndexOf('/') + 1).replace(".git", "");
    }

    private static List<String> cloneAndFindJavaFiles(String repoUrl, String repoFolderName) {
        List<String> javaFiles = new ArrayList<>();
        try {
            // Clone the repo folder
            Process process = Runtime.getRuntime().exec("git clone " + repoUrl);
            process.waitFor();

            // Find Java files
            findJavaFiles(repoFolderName, javaFiles);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return javaFiles;
    }

    private static void findJavaFiles(String folderName, List<String> javaFiles) {
        File folder = new File(folderName);
        File[] files = folder.listFiles();

        if (files == null)
            return;

        for (File file : files) {
            if (file.isDirectory()) {
                findJavaFiles(file.getAbsolutePath(), javaFiles);
            } else if (file.isFile() && file.getName().endsWith(".java")) {
                javaFiles.add(file.getAbsolutePath());
            }
        }
    }

    private static Map<String, Map<String, Integer>> extractClassAnalysis(List<String> javaFiles) {
        Map<String, Map<String, Integer>> classAnalysisMap = new HashMap<>();
        Pattern classPattern = Pattern.compile("\\bclass\\s+([A-Za-z_]\\w*)\\b");
        Pattern methodPattern = Pattern.compile("\\b(?:public|private|protected)\\s+(?:static\\s+)?(?:final\\s+)?(?:\\w+\\s+)?(\\w+)\\s*\\([^)]*\\)\\s*\\{");
        Pattern javadocPattern = Pattern.compile("/\\*\\*.*?\\*/", Pattern.DOTALL);
        Pattern singleLineCommentPattern = Pattern.compile("//.*");
        Pattern blockCommentPattern = Pattern.compile("/\\*[^*].*?\\*/", Pattern.DOTALL);
        Pattern emptyLinePattern = Pattern.compile("^\\s*$", Pattern.MULTILINE); 

        for (String filePath : javaFiles) {
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
                String line;
                StringBuilder content = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                String fileContent = content.toString();
                Matcher classMatcher = classPattern.matcher(fileContent);
                while (classMatcher.find()) {
                    String className = classMatcher.group(1);
                    int totalLines = fileContent.split("\n").length;
                    int emptyLinesCount = countMatches(emptyLinePattern, fileContent);

                    int javadocCount = countJavadocLines(javadocPattern, fileContent);
                    int otherCommentsCount = countMatches(singleLineCommentPattern, fileContent) + countMatches(blockCommentPattern, fileContent);
                    int javadocStartEndLines = countJavadocStartEndLines(javadocPattern, fileContent);
                    int codeLines = totalLines - javadocCount - otherCommentsCount - emptyLinesCount - javadocStartEndLines;

                    int functionCount = countMatches(methodPattern, fileContent);

                    Map<String, Integer> analysis = new HashMap<>();
                    analysis.put("Total Lines", totalLines);
                    analysis.put("Code Lines", codeLines);
                    analysis.put("Javadoc Lines", javadocCount);
                    analysis.put("Other Comment Lines", otherCommentsCount);
                    analysis.put("Empty Lines", emptyLinesCount);
                    analysis.put("Function Count", functionCount);
                    analysis.put("Comment Deviation", calculateCommentDeviation(javadocCount, otherCommentsCount, functionCount, codeLines));

                    classAnalysisMap.put(className, analysis);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return classAnalysisMap;
    }

    private static int countJavadocLines(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        int linesCount = 0;
        while (matcher.find()) {
            String javadocContent = matcher.group();
            String[] lines = javadocContent.split("\n");
            if (lines.length > 2) {
                linesCount += lines.length - 2;  // Exclude the first and last line
            } else if (lines.length == 2) {
                linesCount += 1;  // Only count the content line
            }
        }
        return linesCount;
    }

    private static int countJavadocStartEndLines(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        int linesCount = 0;
        while (matcher.find()) {
            linesCount += 2; // Each javadoc has exactly one start and one end line.
        }
        return linesCount;
    }

    private static int countMatches(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static int calculateCommentDeviation(int javadocLines, int otherCommentLines, int functionCount, int codeLines) {
        double YG = (javadocLines + otherCommentLines) * 0.8 / functionCount;
        double YH = codeLines * 0.3 / functionCount;
        return (int) ((100 * YG / YH) - 100);
    }

    private static void printClassAnalysis(Map<String, Map<String, Integer>> classAnalysisMap) {
        for (Map.Entry<String, Map<String, Integer>> entry : classAnalysisMap.entrySet()) {
            Map<String, Integer> analysis = entry.getValue();
            System.out.println("Sınıf: " + entry.getKey());
            System.out.println("Javadoc Satır Sayısı: " + analysis.get("Javadoc Lines"));
            System.out.println("Yorum Satır Sayısı: " + analysis.get("Other Comment Lines"));
            System.out.println("Kod Satır Sayısı: " + analysis.get("Code Lines"));
            System.out.println("LOC: " + analysis.get("Total Lines"));
            System.out.println("Fonksiyon Sayısı: " + analysis.get("Function Count"));
            System.out.println("Yorum Sapma Yüzdesi: % " + analysis.get("Comment Deviation"));
            System.out.println("-----------------------------------------");
        }
    }
}
