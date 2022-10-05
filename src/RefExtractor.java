import java.io.*;
import java.util.*;

public class RefExtractor {

    private final Map<String, String> refMap = new TreeMap<>();
    private final String refSrc;
    private final String textSrc;

    /**
     * 构造函数会自动构建映射关系
     * [40] ------> Author, year, ......
     * @param refSrc
     * @param textSrc
     */
    public RefExtractor(String refSrc, String textSrc) {
        this.refSrc = refSrc;
        this.textSrc = textSrc;
        convert2Map();
    }

    // 读取引用文件并且整理为HashMap
    private void convert2Map() {
        // 将原本的引用包括标记全部取出
        System.out.println("---------------原文引用------------------");
        if (refSrc == null || refSrc.isEmpty()) {
            System.out.println("引用文件路径错误");
            return;
        }
        // 匹配[num]的形式
        try {
            BufferedReader reader = new BufferedReader(new FileReader(refSrc));
            String str;
            boolean start = false;
            StringBuilder cur = new StringBuilder();
            while ((str = reader.readLine()) != null) {
                boolean startWithLeftBracket = str.startsWith("[");
                if (startWithLeftBracket && !start) {
                    // 如果发现当前字符串左边有个[ 同时start = false，说明找到新的开头
                    start = true;
                    if (!cur.toString().isEmpty()) {
                        insertEntry(cur.toString());
                        cur.delete(0, cur.length());
                    }
                } else if (startWithLeftBracket && start) {
                    // 说明找到了下一个开头
                    start = false;
                    // 上面的所有的内容应该被处理
                    insertEntry(cur.toString());
                    cur.delete(0, cur.length());
                }
                cur.append(str.replace("\\n", " ")).append(" ");
            }
            if (!cur.toString().isEmpty()) {
                 str = cur.toString().replace("\\n", " ");
                 insertEntry(str);
            }
        } catch (Exception e) {
            System.out.println("读取引用失败：" + e.getMessage());
        }
    }

    // 将"[19]" -> "ref" 插入HashMap
    private void insertEntry(String str) {
        int start = 0, end = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == '[') {
                start = i;
            }
            if (str.charAt(i) == ']') {
                end = i;
            }
        }
        String key = str.substring(start, end + 1);
        String value = str.substring(end + 1);
        refMap.put(key, value);
    }

    public Map<String, String> subsetRef() {
        return subsetRef(this.textSrc);
    }

    /**
     *
     * @param textSrc 扫描段落中的引用，对比ref文件中的引用，形成段落的引用子集
     * @return 返回段落中使用的子集的Map
     */
    private Map<String, String> subsetRef(String textSrc) {
        HashMap<String, String> result = new HashMap<>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(textSrc));
            String str;
            StringBuilder cur = new StringBuilder();
            while ((str = reader.readLine()) != null) {
                // 直接将文本变成一个大的字符串处理，避免特殊case
                cur.append(str);
            }
            int open = -1, close = -1;
            str = cur.toString();
            for (int i = 0; i < str.length(); i++) {
                if (str.charAt(i) == '[' && open == -1) open = i;
                if (str.charAt(i) == ']' && open != -1) {
                    // 类似出栈的处理
                    close = i;
                    String refNum = str.substring(open, close + 1);
                    open = -1;

                    // 特殊情况 [48,49]
                    if (refNum.contains(",")) {
                        String[] nums = refNum.split(",");
                        for (int j = 0; j < nums.length; j++) {
                            if (j == 0) {
                                nums[j] += ']';
                            } else if (j == nums.length - 1) {
                                nums[j] = '[' + nums[j];
                            } else {
                                nums[j] = '[' + nums[j] + ']';
                            }
                        }
                        for (String n : nums) {
                            if (refMap.containsKey(n)) {
                                result.put(n, refMap.get(n));
                                System.out.println(String.format("%-5s", n) + refMap.get(n));
                            } else {
                                System.out.println("查询引用失败: " + n);
                            }
                        }
                    } else {
                        if (refMap.containsKey(refNum)) {
                            result.put(refNum, refMap.get(refNum));
                            System.out.println(String.format("%-5s", refNum) + refMap.get(refNum));
                        } else {
                            System.err.println("查询引用失败: " + refNum);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        System.out.println("------------------ DONE ------------------");
        return result;
    }

    /**
     * 将子集中的引用排序，默认从1开始，可以通过重载方法指定offset
     * @param refMap  获取本文引用的子集，参考subsetRef
     */
    public Map<String, String> rankRef(Map<String, String> refMap) {
        return rankRef(refMap, 0);
    }

    /**
     * 无参数的方法会根据传入的Text文件路径进行解析，获取文件名。默认的文件是和Text路径
     * 相同， 并且将名字修改为_updated.txt
     * eg: text.txt -> text_updated.txt
     * @param offset 引用的偏移量
     */
    public void updateTextRef(int offset) {
        int lastIndex = this.textSrc.lastIndexOf(File.separator);
        int dotIndex = 0;
        for (int i = lastIndex; i < textSrc.length(); i++) {
            if (this.textSrc.charAt(i) == '.') {
                dotIndex = i;
                break;
            }
        }
        String textName = this.textSrc.substring(lastIndex + 1, dotIndex);
        String defaultPath = this.textSrc.substring(0, lastIndex + 1) + textName + "_updated.txt";
        updateTextRef(this.textSrc, defaultPath, offset);
    }
    /**
     * 读取排序之后的引用，并且修改原文中的对应引用
     * eg: The text ref is[40] 40在当前的排序中是[1] 则会改成
     *     The text ref is[1]
     * @param textSrc 原文路径
     * @param targetTextSrc 输出的文件路径
     */
    public void updateTextRef(String textSrc, String targetTextSrc, int offset) {
        if (refMap.keySet().isEmpty()) {
            System.out.println("原文引用映射为空！");
            return;
        }
        Map<String, String> outputMap = subsetRef();
        // 根据偏移量形成引用
        Map<String, String> rankedRef = rankRef(outputMap, offset);
        try {
            BufferedReader reader = new BufferedReader(new FileReader(textSrc));


            String str;
            StringBuilder cur = new StringBuilder();
            while ((str = reader.readLine()) != null) {
                // 直接将文本变成一个大的字符串处理，避免特殊case
                cur.append(str);
            }
            int open = -1, close = -1;
            str = cur.toString();
            StringBuilder outputBuilder = new StringBuilder();
            for (int i = 0; i < str.length(); i++) {
                if (str.charAt(i) == '[' && open == -1) {
                    open = i;
                } else if (str.charAt(i) == ']' && open != -1) {
                    // 类似出栈的处理
                    close = i;
                    String refNum = str.substring(open, close + 1);

                    // 特殊情况 [48,49]
                    if (refNum.contains(",")) {
                        String[] nums = refNum.split(",");
                        for (int j = 0; j < nums.length; j++) {
                            if (j == 0) {
                                nums[j] += ']';
                            } else if (j == nums.length - 1) {
                                nums[j] = '[' + nums[j];
                            } else {
                                nums[j] = '[' + nums[j] + ']';
                            }
                        }
                        for (String n : nums) {
                            if (refMap.containsKey(n)) {
                                String replaceRef = rankedRef.get(n);
                                outputBuilder.append(replaceRef);
                            } else {
                                System.err.println("查询引用失败: " + n);
                            }
                        }
                    } else {
                        if (refMap.containsKey(refNum)) {
                            String replaceRef = rankedRef.get(refNum);
                            outputBuilder.append(replaceRef);
                        } else {
                            System.err.println("查询引用失败: " + refNum);
                        }
                    }
                    open = -1;
                } else {
                    // 如果不是[]内的值，就将当前字符append
                    if (open == -1) outputBuilder.append(str.charAt(i));
                }
            }
            BufferedWriter writer = new BufferedWriter(new FileWriter(targetTextSrc));
            String updatedText = outputBuilder.toString();
            writer.write(updatedText);
            writer.close();
            reader.close();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        System.out.println("------------------ DONE ------------------");
    }

    /**
     * 输出原文引用和当前排序之后的引用的映射
     * [48] ---> [1]
     * @param refMap
     * @param offset
     */
    public Map<String, String> rankRef(Map<String, String> refMap, int offset) {
        int counter = 1 + offset;
        TreeMap<String, String> rankedRef = new TreeMap<>();
        System.out.println("-------------------原文引用 --> 子集引用 映射------------------");
        for (String key : refMap.keySet()) {
            System.out.println(String.format("%5s", key + " -----> " +  "[" + counter + "]"));
            rankedRef.put(key, "[" + counter++ + "]");
        }
        System.out.println("------------------ DONE ------------------");

        // 将RankedRef根据Value排序
        List<Map.Entry<String, String>> list = new ArrayList<>(rankedRef.entrySet());
        //升序排序
        Collections.sort(list, (entry1, entry2) -> {
            String o1 = entry1.getValue(), o2 = entry2.getValue();
            int s1 = o1.indexOf('['), e1 = o1.indexOf(']');
            int s2 = o2.indexOf('['), e2 = o2.indexOf(']');
            int num1 = Integer.parseInt(o1.substring(s1 + 1, e1));
            int num2 = Integer.parseInt(o2.substring(s2 + 1, e2));
            return num1 - num2;
        });

        System.out.println("------------------- 子集引用 -----> 原文引用内容映射------------------");
        for (Map.Entry<String, String> entry: list) {
            String key = entry.getKey();
            System.out.println(String.format("%-5s", rankedRef.get(key) + " " + refMap.get(key)));
        }
        System.out.println("------------------ DONE ------------------");
        return rankedRef;
    }

    public TreeMap<String, String> sortByValue(TreeMap<String, String> map) {

        List<Map.Entry<String, String>> list = new ArrayList<>(map.entrySet());

        //升序排序
        Collections.sort(list, (entry1, entry2) -> {
            String o1 = entry1.getValue(), o2 = entry2.getValue();
            int s1 = o1.indexOf('['), e1 = o1.indexOf(']');
            int s2 = o2.indexOf('['), e2 = o2.indexOf(']');
            int num1 = Integer.parseInt(o1.substring(s1 + 1, e1));
            int num2 = Integer.parseInt(o2.substring(s2 + 1, e2));
            return num1 - num2;
        });

        TreeMap<String, String> result = new TreeMap<>();
        for (Map.Entry<String, String> e: list) {
            result.put(e.getKey(), e.getValue());
        }
        return result;
    }

    public static void main(String[] args) {
        RefExtractor main = new RefExtractor(
                "/Users/kolibreath/githubProjects/ReferenceDump/src/ref.txt",
                "/Users/kolibreath/githubProjects/ReferenceDump/src/text.txt"
                );
        main.updateTextRef(13);
    }
}
