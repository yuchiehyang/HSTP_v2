import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TimetableGA {
    // parameters for this project
    private static final int GRADE_CNT = 3;
    private static final int CLASS_CNT = 34;
    private static final int TEACHER_CNT = 143;
    private static final int SUBJECT_CNT = 29;
    private static final int DAY_CNT = 5;
    private static final int PERIOD_CNT = 7;
    // parameter for setting GA algorithm
    private static final int POPULATION_SIZE = 200;
    private static final double MUTATION_RATE = 0.5;
    private static final double CROSSOVER_RATE = 0.5;
    private static final int MAX_GENERATIONS = 3000;

    // IMPORTANT NOTE: 程式陣列長度皆為實際需要長度再加 1
    public static void main(String[] args) throws IOException,
            CsvValidationException {

        // 記錄程式開始執行的時間
        long startTime = System.nanoTime();

        // 記錄班級屬於哪一個年級
        int grade1_classBound = 11;
        int grade2_classBound = 23;
        int[] gradeOfClass = new int[CLASS_CNT + 1];
        for (int i = 0; i < gradeOfClass.length; i++) {
            if (i <= grade1_classBound) {
                gradeOfClass[i] = 7;
            }
            else if (i <= grade2_classBound) {
                gradeOfClass[i] = 8;
            }
            else {
                gradeOfClass[i] = 9;
            }
        }

        // 記錄每個年級各個科目每周需要上的節數 (subjectFixedCnt.csv)
        int[][] subjectFixedCnt = new int[GRADE_CNT + 1][SUBJECT_CNT + 1];
        try (var fr = new FileReader("src/main/resources/subjectFixedCnt.csv", StandardCharsets.UTF_8);
             var reader = new CSVReader(fr)) {
            int gradeIndex = 1;
            int stringIndex = 0;
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                // index 0 不使用，在 subjectFixedCnt.csv 中以 -1 表示
                for (int i = 0; i < subjectFixedCnt[gradeIndex].length; i++) {
                    // index 0 不使用，記錄為 -1
                    if (i == 0) {
                        subjectFixedCnt[gradeIndex][i] = -1;
                    }
                    else {
                        subjectFixedCnt[gradeIndex][i] = Integer.parseInt(nextLine[stringIndex]);
                        stringIndex++;
                    }
                }
                gradeIndex++;
                stringIndex = 0;
            }
        }

        // 記錄每節課各科目教室最多能容納幾個班級上課 (subjectRoomLimit.csv)
        int[] subjectRoomLimit = new int[SUBJECT_CNT + 1];
        try (var fr = new FileReader("src/main/resources/subjectRoomLimit.csv", StandardCharsets.UTF_8);
             var reader = new CSVReader(fr)) {
            int stringIndex = 0;
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                for (int i = 0; i < subjectRoomLimit.length; i++) {
                    // index 0 不使用，記錄為 -1
                    if (i == 0) {
                        subjectRoomLimit[i] = -1;
                    }
                    else {
                        subjectRoomLimit[i] = Integer.parseInt(nextLine[stringIndex]);
                        stringIndex++;
                    }
                }
            }
        }

        // 記錄每個老師可以教授的科目，可授課的科目為 1，不可授課為 0 (teacherTeaching.csv)
        int[][] teacherTeaching = new int[TEACHER_CNT + 1][SUBJECT_CNT + 1];
        try (var fr = new FileReader("src/main/resources/teacherTeaching.csv", StandardCharsets.UTF_8);
             var reader = new CSVReader(fr)) {
            int teacherIndex = 1;
            int stringIndex = 0;
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                for (int i = 0; i < teacherTeaching[teacherIndex].length; i++) {
                    // index 0 不使用，記錄為 -1
                    if (i == 0) {
                        teacherTeaching[teacherIndex][i] = -1;
                    }
                    else {
                        teacherTeaching[teacherIndex][i] = Integer.parseInt(nextLine[stringIndex]);
                        stringIndex++;
                    }
                }
                teacherIndex++;
                stringIndex = 0;
            }
        }

        // TODO: 記錄老師偏好的授課的時間
        int[][][][] teacherPreferredTimetable = new int[POPULATION_SIZE + 1][TEACHER_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
        // 記錄老師該節次是否為空堂 (0 為空堂，1 為有課)
        int[][][][] teacherActualTimetable = new int[POPULATION_SIZE + 1][TEACHER_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
        // 記錄每個班級各個科目一周上了幾次
        int[][][] classSubjectCnt = new int[POPULATION_SIZE + 1][CLASS_CNT + 1][SUBJECT_CNT + 1];
        // 記錄每節課各科目教室有幾個班級在使用
        int[][][][] subjectRoomCnt = new int[POPULATION_SIZE + 1][SUBJECT_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
        // 記錄每個班級各節次上了什麼科目
        int[][][][] classSubjectTable = new int[POPULATION_SIZE + 1][SUBJECT_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
        // 記錄每個班級各節次的上課老師
        int[][][][] classTeacherTable = new int[POPULATION_SIZE + 1][SUBJECT_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
        // 記錄每個班級是否已經有指定的科目老師 (classAssignedTeacher)
        // 若有則填入該老師 id，無則為 0
        int[][] classAssignedTeacher = new int[CLASS_CNT + 1][SUBJECT_CNT + 1];
        try (var fr = new FileReader("src/main/resources/classAssignedTeacher.csv", StandardCharsets.UTF_8);
             var reader = new CSVReader(fr)) {
            int classIndex = 1;
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                for (int stringIndex = 0; stringIndex < nextLine.length; stringIndex++) {
                    int assignedTeacherId = Integer.parseInt(nextLine[stringIndex]);
                    for (int subjectId = 0; subjectId < classAssignedTeacher[classIndex].length; subjectId++) {
                        // index 0 不使用，記錄為 -1
                        if (subjectId == 0) {
                            classAssignedTeacher[classIndex][subjectId] = -1;
                        }
                        else {
                            if (teacherTeaching[assignedTeacherId][subjectId] == 1) {
                                classAssignedTeacher[classIndex][subjectId] = assignedTeacherId;
                            }
                        }
                    }
                    classIndex++;
                }
            }
        }

        // 建立初始解
        // TODO: 如何建置重排結構?
        // while + continue 的 label?
        for (int populationIndex = 0; populationIndex < POPULATION_SIZE; populationIndex++) {

        }

        // 記錄程式執行完畢的時間
        long endTime = System.nanoTime();
        // 計算執行程式的 elapsed time (in nanoseconds)
        long timeElapsed = endTime - startTime;
        System.out.println("Execution time: " + timeElapsed / 1000000000 / 60 + " minutes " + timeElapsed / 1000000000 % 60 + " seconds");

    }
}
