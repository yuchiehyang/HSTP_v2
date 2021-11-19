import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import java.util.ArrayList;
import java.util.Collections;

public class TimetableGA {
    // parameters for this project
    private static final int GRADE_CNT = 3;
    private static final int CLASS_CNT = 34;
    private static final int TEACHER_CNT = 143;
    private static final int SUBJECT_CNT = 29;
    private static final int DAY_CNT = 5;
    private static final int PERIOD_CNT = 7;
    // parameter for setting GA algorithm
    private static final int POPULATION_SIZE = 6;
    private static final double MUTATION_RATE = 0.5;
    private static final double CROSSOVER_RATE = 0.5;
    private static final int ELITISM_COUNT = 1;
    private static final int MAX_GENERATIONS = 1;

    //ATTENTION 1107 : connection ouput usages
    static String jdbcURL = "jdbc:mysql://localhost:3306/hstp?sessionVariables=sql_mode='NO_ENGINE_SUBSTITUTION'&jdbcCompliantTruncation=false";
    static String username = "root";
    static String password = "m0920776286";

    // IMPORTANT NOTE: 程式陣列長度皆為實際需要長度再加 1
    public static void main(String[] args) throws IOException, CsvValidationException, SQLException {

        // parameter for DEBUG mode
        boolean DEBUG = false;

        System.out.println("Running initialization...");
        // 記錄程式開始執行的時間
        long startTime = System.nanoTime();

        // 記錄班級屬於哪一個年級
        int grade1_classBound = 11;
        int grade2_classBound = 23;
        int[] gradeOfClass = new int[CLASS_CNT + 1];
        // index 0 不使用
        for (int i = 1; i < gradeOfClass.length; i++) {
            if (i <= grade1_classBound) {
                gradeOfClass[i] = 1;
            }
            else if (i <= grade2_classBound) {
                gradeOfClass[i] = 2;
            }
            else {
                gradeOfClass[i] = 3;
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
                // index 0 不使用
                for (int i = 1; i < subjectFixedCnt[gradeIndex].length; i++) {
                    subjectFixedCnt[gradeIndex][i] = Integer.parseInt(nextLine[stringIndex]);
                    stringIndex++;
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
                // index 0 不使用
                for (int i = 1; i < subjectRoomLimit.length; i++) {
                    subjectRoomLimit[i] = Integer.parseInt(nextLine[stringIndex]);
                    stringIndex++;
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
                // index 0 不使用
                for (int i = 1; i < teacherTeaching[teacherIndex].length; i++) {
                    teacherTeaching[teacherIndex][i] = Integer.parseInt(nextLine[stringIndex]);
                    stringIndex++;
                }
                teacherIndex++;
                stringIndex = 0;
            }
        }
        //Attention --chloe: 記錄老師偏好的授課的時間，可授課的時間為 1，不可授課的時間為 0
        int[][] teacherPreferredTimetable = new int[TEACHER_CNT + 1][SUBJECT_CNT + 1];
        try (var fr = new FileReader("src/main/resources/teacherPreferTeachingTime.csv", StandardCharsets.UTF_8);
             var reader = new CSVReader(fr)) {
            int teacherIndex = 1;
            int stringIndex = 0;
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                // index 0 不使用
                for (int i = 1; i < teacherPreferredTimetable[teacherIndex].length; i++) {
                    teacherPreferredTimetable[teacherIndex][i] = Integer.parseInt(nextLine[stringIndex]);
                    stringIndex++;
                }
                teacherIndex++;
                stringIndex = 0;
            }
        }

        // 記錄老師該節次是否為空堂 (0 為空堂，1 為有課)
        int[][][][] teacherActualTimetable = new int[POPULATION_SIZE + 1][TEACHER_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
        for (int pop = 0; pop < (POPULATION_SIZE + 1); pop++) {
            for (int t = 0; t < (TEACHER_CNT + 1); t++) {
                for (int d = 0; d < (DAY_CNT + 1); d++) {
                    for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                        teacherActualTimetable[pop][t][d][p] = 0;
                    }
                }
            }
        }
        // 記錄每個班級各個科目一周上了幾次
        int[][][] classSubjectCnt = new int[POPULATION_SIZE + 1][CLASS_CNT + 1][SUBJECT_CNT + 1];
        for (int pop = 0; pop < (POPULATION_SIZE + 1); pop++) {
            for (int c = 0; c < (CLASS_CNT + 1); c++) {
                for (int s = 0; s < (SUBJECT_CNT + 1); s++) {
                    classSubjectCnt[pop][c][s] = 0;
                }
            }
        }
        // 記錄每節課各科目教室有幾個班級在使用
        int[][][][] subjectRoomCnt = new int[POPULATION_SIZE + 1][SUBJECT_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
        for (int pop = 0; pop < (POPULATION_SIZE + 1); pop++) {
            for (int s = 0; s < (SUBJECT_CNT + 1); s++) {
                for (int d = 0; d < (DAY_CNT + 1); d++) {
                    for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                        subjectRoomCnt[pop][s][d][p] = 0;
                    }
                }
            }
        }
        // 記錄每個班級各節次上了什麼科目
        int[][][][] classSubjectTable = new int[POPULATION_SIZE + 1][CLASS_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
        for (int pop = 0; pop < (POPULATION_SIZE + 1); pop++) {
            for (int c = 0; c < (CLASS_CNT + 1); c++) {
                for (int d = 0; d < (DAY_CNT + 1); d++) {
                    for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                        classSubjectTable[pop][c][d][p] = 0;
                    }
                }
            }
        }
        // 記錄每個班級各節次的上課老師
        int[][][][] classTeacherTable = new int[POPULATION_SIZE + 1][CLASS_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
        for (int pop = 0; pop < (POPULATION_SIZE + 1); pop++) {
            for (int c = 0; c < (CLASS_CNT + 1); c++) {
                for (int d = 0; d < (DAY_CNT + 1); d++) {
                    for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                        classTeacherTable[pop][c][d][p] = 0;
                    }
                }
            }
        }
        // 記錄每個班級是否已經有指定的科目老師 (classAssignedTeacher.csv)
        // 若有則填入該老師 id，無則為 0
        // 保留最原始的 classAssignedTeacher，並記錄為 default_classAssignedTeacher
        int[][] default_classAssignedTeacher = new int [CLASS_CNT + 1][SUBJECT_CNT + 1];
        try (var fr = new FileReader("src/main/resources/classAssignedTeacher.csv", StandardCharsets.UTF_8);
             var reader = new CSVReader(fr)) {
            int classIndex = 1;
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                for (String s : nextLine) {
                    int assignedTeacherId = Integer.parseInt(s);
                    // index 0 不使用
                    for (int subjectId = 1; subjectId < (SUBJECT_CNT + 1); subjectId++) {
                        // 周會 (id: 20)、聯課 (id: 21) 的老師不要登記，隨機指派
                        int schoolMeetingId = 20;
                        int clubCourseId = 21;
                        if ((teacherTeaching[assignedTeacherId][subjectId] == 1)
                                && (subjectId != schoolMeetingId) && (subjectId != clubCourseId)) {
                            default_classAssignedTeacher[classIndex][subjectId] = assignedTeacherId;
                        }
                    }
                    classIndex++;
                }
            }
        }
        int[][][] classAssignedTeacher = new int[POPULATION_SIZE + 1][CLASS_CNT + 1][SUBJECT_CNT + 1];
        for (int populationIndex = 0; populationIndex < (POPULATION_SIZE + 1); populationIndex++) {
            for (int c = 0; c < (CLASS_CNT + 1); c++) {
                for (int s = 0; s < (SUBJECT_CNT + 1); s++) {
                    classAssignedTeacher[populationIndex][c][s] = default_classAssignedTeacher[c][s];
                }
            }
        }

        // 建立初始解
        for (int populationIndex = 0; populationIndex < (POPULATION_SIZE + 1); populationIndex++) {

            int[][][] temp_teacherActualTimetable;
            int[][] temp_classSubjectCnt;
            int[][][] temp_subjectRoomCnt;
            int[][][] temp_classSubjectTable;
            int[][][] temp_classTeacherTable;
            int[][] temp_classAssignedTeacher;

            boolean isArrangingAllClass = true;
            REARRANGE_ALL_CLASS:
            while (isArrangingAllClass) {

                boolean arrangeAllClassAgain = false;

//                System.out.println("(Re)start arranging all classes");

                // 初始化重排課用的陣列
                temp_teacherActualTimetable = new int[TEACHER_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
                temp_classSubjectCnt = new int [CLASS_CNT + 1][SUBJECT_CNT + 1];
                temp_subjectRoomCnt = new int[SUBJECT_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
                temp_classSubjectTable = new int[CLASS_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
                temp_classTeacherTable = new int[CLASS_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
                temp_classAssignedTeacher = new int[CLASS_CNT + 1][SUBJECT_CNT + 1];
                for (int c = 0; c < (CLASS_CNT + 1); c++) {
                    for (int s = 0; s < (SUBJECT_CNT + 1); s++) {
                        temp_classAssignedTeacher[c][s] = default_classAssignedTeacher[c][s];
                    }
                }

                // 記錄全部班級排課失敗的次數
                int totalFailCnt = 0;
                // 安排每個班級的固定課程 & 兩堂連堂的體育課
                // 略過 classIndex == 1
                for (int classIndex = 1; classIndex < (CLASS_CNT + 1); classIndex++) {

                    // 安排固定時間課程 - 班會
                    int classMeetingId = 22;
                    int classMeetingDay = 1;
                    int classMeetingPeriod = 1;
                    // 選班會的老師
                    int classMeetingTeacherId = temp_classAssignedTeacher[classIndex][classMeetingId];
                    // 記錄班會的時間
                    temp_classSubjectTable[classIndex][classMeetingDay][classMeetingPeriod] = classMeetingId;
                    temp_classSubjectCnt[classIndex][classMeetingId]++;
                    // 記錄班會的老師
                    temp_classTeacherTable[classIndex][classMeetingDay][classMeetingPeriod] = classMeetingTeacherId;
                    temp_teacherActualTimetable[classMeetingTeacherId][classMeetingDay][classMeetingPeriod]++;
                    // 記錄班會使用的教室
                    temp_subjectRoomCnt[classMeetingId][classMeetingDay][classMeetingPeriod]++;

                    // 安排固定課程 - 周會
                    int schoolMeetingId = 20;
                    int schoolMeetingDay = 2;
                    int schoolMeetingPeriod = 3;
                    // 選周會的老師
                    int schoolMeetingTeacherId = 0;
                    ArrayList<Integer> randomNumberList = new ArrayList<>();
                    for (int i = 110; i <= 143; i++) {
                        randomNumberList.add(i);
                    }
                    Collections.shuffle(randomNumberList);
                    int listIndex = 0;
                    do {
                        // 如果沒有老師能配合該班級在這時間上課，重新排全部班級的課
                        if (listIndex >= randomNumberList.size()) {
                            arrangeAllClassAgain = true;
                            break;
                        }
                        schoolMeetingTeacherId = randomNumberList.get(listIndex);
                        listIndex++;
                    } while((teacherTeaching[schoolMeetingTeacherId][schoolMeetingId] == 0) ||
                            (temp_teacherActualTimetable[schoolMeetingTeacherId][schoolMeetingDay][schoolMeetingPeriod] == 1));
                    randomNumberList.clear();

                    if (arrangeAllClassAgain) {
//                        System.out.print("\tUnknown Error: ");
//                        System.out.println("Jump to REARRANGE_ALL_CLASS when arrange class " + classIndex + "'s teacher for school meeting");
                        break;
                    }

                    // 記錄周會的時間
                    temp_classSubjectTable[classIndex][schoolMeetingDay][schoolMeetingPeriod] = schoolMeetingId;
                    temp_classSubjectCnt[classIndex][schoolMeetingId]++;
                    // 記錄周會的老師
                    temp_classAssignedTeacher[classIndex][schoolMeetingId] = schoolMeetingTeacherId;
                    temp_classTeacherTable[classIndex][schoolMeetingDay][schoolMeetingPeriod] = schoolMeetingTeacherId;
                    temp_teacherActualTimetable[schoolMeetingTeacherId][schoolMeetingDay][schoolMeetingPeriod]++;
                    // 記錄周會使用的教室
                    temp_subjectRoomCnt[schoolMeetingId][schoolMeetingDay][schoolMeetingPeriod]++;

                    // 安排固定課程 - 聯課
                    int clubCourseId = 21;
                    int clubCourseDay = 2;
                    int clubCoursePeriod = 4;
                    // 選聯課的老師
                    int clubCourseTeacherId = 0;
                    for (int i = 1; i <= TEACHER_CNT; i++) {
                        randomNumberList.add(i);
                    }
                    Collections.shuffle(randomNumberList);
                    listIndex = 0;
                    do {
                        // 如果沒有老師能配合該班級在這時間上課，重新排全部班級的課
                        if (listIndex >= randomNumberList.size()) {
                            arrangeAllClassAgain = true;
                            break;
                        }
                        clubCourseTeacherId = randomNumberList.get(listIndex);
                        listIndex++;
                    } while((teacherTeaching[clubCourseTeacherId][clubCourseId] == 0) ||
                            (temp_teacherActualTimetable[clubCourseTeacherId][clubCourseDay][clubCoursePeriod] == 1));
                    randomNumberList.clear();

                    if (arrangeAllClassAgain) {
//                        System.out.print("\tUnknown Error: ");
//                        System.out.println("Jump to REARRANGE_ALL_CLASS when arrange class " + classIndex + "'s teacher for club course");
                        break;
                    }

                    // 記錄聯課的時間
                    temp_classSubjectTable[classIndex][clubCourseDay][clubCoursePeriod] = clubCourseId;
                    temp_classSubjectCnt[classIndex][clubCourseId]++;
                    // 記錄聯課的老師
                    temp_classAssignedTeacher[classIndex][clubCourseId] = clubCourseTeacherId;
                    temp_classTeacherTable[classIndex][clubCourseDay][clubCoursePeriod] = clubCourseTeacherId;
                    temp_teacherActualTimetable[clubCourseTeacherId][clubCourseDay][clubCoursePeriod]++;
                    // 記錄聯課使用的教室
                    temp_subjectRoomCnt[clubCourseId][clubCourseDay][clubCoursePeriod]++;

                    // 安排兩堂連一起的體育課
                    int peId = 13;
                    int peClassFailureCnt = 0;
                    // 選班級上體育課的時間
                    int peDay = 0;
                    int pePeriod = 0;
                    boolean noEmptyRoom, isImpossiblePeriod, notValidTimeForClass;
                    do {
                        // 如果體育課時間一直選不出來，重頭開始排課
                        if (peClassFailureCnt > 1000000) {
                            arrangeAllClassAgain = true;
                            break;
                        }
                        peDay = ThreadLocalRandom.current().nextInt(1, DAY_CNT + 1);
                        pePeriod = ThreadLocalRandom.current().nextInt(1, PERIOD_CNT + 1);
                        if (peDay != 0 && pePeriod != 0) {
                            peClassFailureCnt++;
                        }
                        isImpossiblePeriod = (pePeriod == 4) || (pePeriod == PERIOD_CNT);
                        if (isImpossiblePeriod) {
                            noEmptyRoom = true;
                            notValidTimeForClass = true;
                        }
                        else {
                            noEmptyRoom = (temp_subjectRoomCnt[peId][peDay][pePeriod] >= subjectRoomLimit[peId]) ||
                                    (temp_subjectRoomCnt[peId][peDay][pePeriod + 1] >= subjectRoomLimit[peId]);
                            // 體育課不能和班會、周會、聯課衝堂
                            notValidTimeForClass = (temp_classSubjectTable[classIndex][peDay][pePeriod] != 0) ||
                                    (temp_classSubjectTable[classIndex][peDay][pePeriod + 1] != 0);
                        }
                    } while (isImpossiblePeriod || noEmptyRoom ||  notValidTimeForClass);

                    if (arrangeAllClassAgain) {
//                        System.out.println("\tJump to REARRANGE_ALL_CLASS when arrange class " + classIndex + "'s time for PE class");
                        break;
                    }

                    // 選體育老師
                    int peTeacherId = 0;
                    boolean notValidTeacher, notValidTimeForTeacher;
                    for (int i = 1; i <= TEACHER_CNT; i++) {
                        randomNumberList.add(i);
                    }
                    Collections.shuffle(randomNumberList);
                    listIndex = 0;
                    do {
                        // 如果沒有老師能配合該班級在這時間上體育課，重新排全部班級的課
                        if (listIndex >= randomNumberList.size()) {
                            arrangeAllClassAgain = true;
                            break;
                        }
                        peTeacherId = randomNumberList.get(listIndex);
                        listIndex++;
                        notValidTeacher = teacherTeaching[peTeacherId][peId] == 0;
                        notValidTimeForTeacher = (temp_teacherActualTimetable[peTeacherId][peDay][pePeriod] == 1) ||
                                (temp_teacherActualTimetable[peTeacherId][peDay][pePeriod + 1] == 1);
                    } while (notValidTeacher || notValidTimeForTeacher);
                    randomNumberList.clear();

                    if (arrangeAllClassAgain) {
//                        System.out.println("\tJump to REARRANGE_ALL_CLASS when arrange class " + classIndex + "'s teacher for PE class");
                        break;
                    }

                    // 記錄體育課的時間 (一次兩堂)
                    temp_classSubjectTable[classIndex][peDay][pePeriod] = peId;
                    temp_classSubjectTable[classIndex][peDay][pePeriod + 1] = peId;
                    temp_classSubjectCnt[classIndex][peId] += 2;
                    // 記錄體育課的老師
                    if (temp_classAssignedTeacher[classIndex][peId] == 0) {
                        temp_classAssignedTeacher[classIndex][peId] = peTeacherId;
                    }
                    temp_classTeacherTable[classIndex][peDay][pePeriod] = peTeacherId;
                    temp_classTeacherTable[classIndex][peDay][pePeriod + 1] = peTeacherId;
                    temp_teacherActualTimetable[peTeacherId][peDay][pePeriod]++;
                    temp_teacherActualTimetable[peTeacherId][peDay][pePeriod + 1]++;
                    // 記錄體育課使用的教室
                    temp_subjectRoomCnt[peId][peDay][pePeriod]++;
                    temp_subjectRoomCnt[peId][peDay][pePeriod + 1]++;
                }

                if (arrangeAllClassAgain) {
                    continue REARRANGE_ALL_CLASS;
                }

                // 開始排每個班級的單堂課程
                int[][][] single_teacherActualTimetable = new int[TEACHER_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
                int[][] single_classSubjectCnt = new int [CLASS_CNT + 1][SUBJECT_CNT + 1];
                int[][][] single_subjectRoomCnt = new int[SUBJECT_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
                int[][][] single_classSubjectTable = new int[CLASS_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
                int[][][] single_classTeacherTable = new int[CLASS_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
                int[][] single_classAssignedTeacher = new int[CLASS_CNT + 1][SUBJECT_CNT + 1];
                // 略過 classIndex == 0
                for (int classIndex = 1; classIndex < (CLASS_CNT + 1); classIndex++) {

                    int singleClassFailureCnt = 0;
                    boolean isArrangingSingleClass = true;
                    REARRANGE_SINGLE_CLASS:
                    while (isArrangingSingleClass) {

                        // 複製前面排固定課程 & 體育課的陣列
                        for (int t = 0; t < (TEACHER_CNT + 1); t++) {
                            for (int d = 0; d < (DAY_CNT + 1); d++) {
                                for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                                    single_teacherActualTimetable[t][d][p] = temp_teacherActualTimetable[t][d][p];
                                }
                            }
                        }
                        for (int c = 0; c < (CLASS_CNT + 1); c++) {
                            for (int s = 0; s < (SUBJECT_CNT + 1); s++) {
                                single_classSubjectCnt[c][s] = temp_classSubjectCnt[c][s];
                                single_classAssignedTeacher[c][s] = temp_classAssignedTeacher[c][s];
                            }
                        }
                        for (int s = 0; s < (SUBJECT_CNT + 1); s++) {
                            for (int d = 0; d < (DAY_CNT + 1); d++) {
                                for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                                    single_subjectRoomCnt[s][d][p] = temp_subjectRoomCnt[s][d][p];
                                }
                            }
                        }
                        for (int c = 0; c < (CLASS_CNT + 1); c++) {
                            for (int d = 0; d < (DAY_CNT + 1); d++) {
                                for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                                    single_classSubjectTable[c][d][p] = temp_classSubjectTable[c][d][p];
                                    single_classTeacherTable[c][d][p] = temp_classTeacherTable[c][d][p];
                                }
                            }
                        }

                        boolean arrangeSingleClassAgain = false;
//                        System.out.println("(Re)start arranging a single class: " + classIndex);

                        // 略過 subjectIndex == 0
                        for (int subjectIndex = 1; subjectIndex < (SUBJECT_CNT + 1); subjectIndex++) {

                            // 略過班會、周會、聯課、體育課
                            int classMeetingId = 22;
                            int schoolMeetingId = 20;
                            int clubCourseId = 21;
                            int peId = 13;
                            if (subjectIndex == classMeetingId || subjectIndex == schoolMeetingId ||
                                    subjectIndex == clubCourseId || subjectIndex == peId) {
                                continue ;
                            }

                            while (single_classSubjectCnt[classIndex][subjectIndex] < subjectFixedCnt[gradeOfClass[classIndex]][subjectIndex]) {

                                // 重排條件 1
                                if (totalFailCnt > 10000) {
//                                    System.out.println("\tJump to REARRANGE_ALL_CLASS when populationIndex: " + populationIndex +  "\t classIndex: " + classIndex);
                                    arrangeAllClassAgain = true;
                                    totalFailCnt = 0;
                                    break;
                                }

                                // 選單堂課程的老師
                                int teacherId = 0;
                                ArrayList<Integer> randomNumberList = new ArrayList<>();
                                for (int i = 1; i <= TEACHER_CNT; i++) {
                                    randomNumberList.add(i);
                                }
                                Collections.shuffle(randomNumberList);
                                int listIndex = 0;
                                do {
                                    if (single_classAssignedTeacher[classIndex][subjectIndex] == 0) {
                                        // 如果沒有老師能配合該班級上課
                                        if (listIndex >= randomNumberList.size()) {
                                            arrangeAllClassAgain = true;
                                            break;
                                        }
                                        teacherId = randomNumberList.get(listIndex);
                                        listIndex++;
                                    }
                                    else {
                                        teacherId = single_classAssignedTeacher[classIndex][subjectIndex];
                                    }
                                } while(teacherTeaching[teacherId][subjectIndex] == 0);
                                randomNumberList.clear();
                                if (arrangeAllClassAgain) {
//                                    System.out.print("\tUnknown Error: ");
//                                    System.out.println("Jump to REARRANGE_ALL_CLASS when arrange class " + classIndex + "'s teacher for subject " + subjectIndex);
                                    break;
                                }

                                // 選單堂課程的時間 (要班級 & 老師皆可以的時間)
                                int day = 0;
                                int period = 0;
                                boolean noEmptyRoom, invalidForClass, invalidForTeacher;
                                do {

                                    if (day != 0 && period != 0) {
                                        singleClassFailureCnt++;
                                    }
                                    // 如果持續找不到班級&老師能配對的時間，重新安排該班級的課程
                                    if (singleClassFailureCnt > 9999) {
                                        arrangeSingleClassAgain = true;
                                        totalFailCnt++;
                                        singleClassFailureCnt = 0;
                                        break;
                                    }

                                    day = ThreadLocalRandom.current().nextInt(1, DAY_CNT + 1);
                                    period = ThreadLocalRandom.current().nextInt(1, PERIOD_CNT + 1);
                                    noEmptyRoom = single_subjectRoomCnt[subjectIndex][day][period] >= subjectRoomLimit[subjectIndex];
                                    invalidForClass = single_classSubjectTable[classIndex][day][period] != 0;
                                    invalidForTeacher = single_teacherActualTimetable[teacherId][day][period] != 0;

                                } while (noEmptyRoom || invalidForClass || invalidForTeacher);
                                if (arrangeSingleClassAgain) {
//                                    System.out.println("\tJump to REARRANGE_SINGLE_CLASS when arrange class " + classIndex + "'s time for subject " +  subjectIndex);
                                    break;
                                }

                                // 記錄該課程的時間
                                single_classSubjectTable[classIndex][day][period] = subjectIndex;
                                single_classSubjectCnt[classIndex][subjectIndex]++;
                                // 記錄該課程的老師
                                if (single_classAssignedTeacher[classIndex][subjectIndex] == 0) {
                                    single_classAssignedTeacher[classIndex][subjectIndex] = teacherId;
                                }
                                single_classTeacherTable[classIndex][day][period] = teacherId;
                                single_teacherActualTimetable[teacherId][day][period]++;
                                // 記錄該課程的教室
                                single_subjectRoomCnt[subjectIndex][day][period]++;

                            }

                            if (arrangeAllClassAgain) {
                                break;
                            }
                            if (arrangeSingleClassAgain) {
                                break;
                            }
                        }

                        if (arrangeAllClassAgain) {
                            break;
                        }
                        if (arrangeSingleClassAgain) {
                            continue REARRANGE_SINGLE_CLASS;
                        }

                        isArrangingSingleClass = false;

                        // 確認該班級排課完畢才複製到 temp 的陣列
                        for (int t = 0; t < (TEACHER_CNT + 1); t++) {
                            for (int d = 0; d < (DAY_CNT + 1); d++) {
                                for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                                    temp_teacherActualTimetable[t][d][p] = single_teacherActualTimetable[t][d][p];
                                }
                            }
                        }
                        for (int c = 0; c < (CLASS_CNT + 1); c++) {
                            for (int s = 0; s < (SUBJECT_CNT + 1); s++) {
                                temp_classSubjectCnt[c][s] = single_classSubjectCnt[c][s];
                                temp_classAssignedTeacher[c][s] = single_classAssignedTeacher[c][s];
                            }
                        }
                        for (int s = 0; s < (SUBJECT_CNT + 1); s++) {
                            for (int d = 0; d < (DAY_CNT + 1); d++) {
                                for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                                    temp_subjectRoomCnt[s][d][p] = single_subjectRoomCnt[s][d][p];
                                }
                            }
                        }
                        for (int c = 0; c < (CLASS_CNT + 1); c++) {
                            for (int d = 0; d < (DAY_CNT + 1); d++) {
                                for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                                    temp_classSubjectTable[c][d][p] = single_classSubjectTable[c][d][p];
                                    temp_classTeacherTable[c][d][p] = single_classTeacherTable[c][d][p];
                                }
                            }
                        }
                    }

                    if (arrangeAllClassAgain) {
                        break;
                    }
                }

                if (arrangeAllClassAgain) {
                    continue REARRANGE_ALL_CLASS;
                }

                isArrangingAllClass = false;

                // 確認每個班級皆排課完畢才複製到真正後續 GA 會使用的陣列
                for (int t = 0; t < (TEACHER_CNT + 1); t++) {
                    for (int d = 0; d < (DAY_CNT + 1); d++) {
                        for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                            teacherActualTimetable[populationIndex][t][d][p] = temp_teacherActualTimetable[t][d][p];
                        }
                    }
                }
                for (int c = 0; c < (CLASS_CNT + 1); c++) {
                    for (int s = 0; s < (SUBJECT_CNT + 1); s++) {
                        classSubjectCnt[populationIndex][c][s] = temp_classSubjectCnt[c][s];
                        classAssignedTeacher[populationIndex][c][s] = temp_classAssignedTeacher[c][s];
                    }
                }
                for (int s = 0; s < (SUBJECT_CNT + 1); s++) {
                    for (int d = 0; d < (DAY_CNT + 1); d++) {
                        for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                            subjectRoomCnt[populationIndex][s][d][p] = temp_subjectRoomCnt[s][d][p];
                        }
                    }
                }
                for (int c = 0; c < (CLASS_CNT + 1); c++) {
                    for (int d = 0; d < (DAY_CNT + 1); d++) {
                        for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                            classSubjectTable[populationIndex][c][d][p] = temp_classSubjectTable[c][d][p];
                            classTeacherTable[populationIndex][c][d][p] = temp_classTeacherTable[c][d][p];
                        }
                    }
                }
            }
        }

        if (DEBUG) {
            System.out.println("Running calculation for fitness...");
        }

        // 計算以軟限制式計算適應度
        double[] objectVal = new double[POPULATION_SIZE + 1];
        double[] fitness = new double[POPULATION_SIZE + 1];
        // SC1: 減少教師的空堂時間 (15)
        double[][][][] J = new double[POPULATION_SIZE + 1][TEACHER_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
        double[] fitnessJ = new double[POPULATION_SIZE + 1];
        for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
            for (int tid = 1; tid < (TEACHER_CNT + 1); tid++) {
                for (int day = 1; day < (DAY_CNT + 1); day++) {
                    for (int period = 2; period < 7; period++) {
                        J[popIndex][tid][day][period] = (double) teacherActualTimetable[popIndex][tid][day][period - 1]
                                - (double) teacherActualTimetable[popIndex][tid][day][period]
                                + (double) teacherActualTimetable[popIndex][tid][day][period + 1] - 1;
                        // SC1: 減少教師的空堂時間 (16)
                        if (J[popIndex][tid][day][period] < 0) {
                            J[popIndex][tid][day][period] = 0;
                        }
                        fitnessJ[popIndex] += J[popIndex][tid][day][period];
                    }
                }
            }
        }

        // SC2: 降低教師每周的工作日 (17)
        double[][][] Y = new double[POPULATION_SIZE + 1][TEACHER_CNT + 1][DAY_CNT + 1];
        double[][] D = new double[POPULATION_SIZE + 1][TEACHER_CNT + 1];
        double[] fitnessY = new double[POPULATION_SIZE + 1];
        for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
            for (int tid = 1; tid < (TEACHER_CNT + 1); tid++) {
                for (int day = 1; day < (DAY_CNT + 1); day++) {
                    for (int period = 1; period < (PERIOD_CNT + 1); period++) {
                        if (teacherActualTimetable[popIndex][tid][day][period] != 0) {
                            Y[popIndex][tid][day] = 1;
                            break;
                        }
                    }
                    D[popIndex][tid] += Y[popIndex][tid][day];
                }
                fitnessY[popIndex] += D[popIndex][tid];
            }
        }


        // 整理老師每周最低上課時數的數據，可用 csv 檔匯入
        // 記錄老師每周最低上課時數的數據 (minTeachingTime.csv)
        int O[] = new int[TEACHER_CNT+ 1];
        try (var fr = new FileReader("src/main/resources/minTeachingTime.csv", StandardCharsets.UTF_8);
             var reader = new CSVReader(fr)) {
            int stringIndex = 0;
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                for (int i = 1; i < O.length; i++) {
                    O[i] = Integer.parseInt(nextLine[stringIndex]);
                    stringIndex++;
                }
            }
        }
        // SC3: 平衡教師不需要工作的天數 (19)
        double MD[][] = new double[POPULATION_SIZE+1][TEACHER_CNT+1];
        for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
            for (int tid = 1; tid < (TEACHER_CNT + 1); tid++) {
                MD[popIndex][tid] = O[tid] / (PERIOD_CNT+1);
            }
        }

        // SC3: 平衡教師不需要工作的天數 (20)
        double BETA[] = new double[POPULATION_SIZE+1];
        double extra[][] = new double[POPULATION_SIZE+1][TEACHER_CNT+1];
        for(int popIndex = 0; popIndex<((POPULATION_SIZE+1));popIndex++){
            for (int tid = 1; tid < (TEACHER_CNT + 1); tid++) {
                extra[popIndex][tid] = D[popIndex][tid] = MD[popIndex][tid];
                if(extra[popIndex][tid]>BETA[popIndex]){
                    BETA[popIndex] = extra[popIndex][tid];
                }

            }
        }

        // SC4: 降低教師每周的實際工作節次數 (22)
        double M[][] = new double[POPULATION_SIZE+1][TEACHER_CNT+1];   //老師t一周實際工作節次數量
        for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
            for (int tid = 1; tid < (TEACHER_CNT + 1); tid++) {
                for( int day = 1; day<(DAY_CNT+1);day++) {
                    for (int period = 1; period < (PERIOD_CNT + 1); period++) {
                        M[popIndex][tid] += teacherActualTimetable[popIndex][tid][day][period];
                    }
                }
            }
        }
        // SC4: 降低教師每周的實際工作節次數 (23)
        double teacherExtraPeriods [][] = new double[POPULATION_SIZE+1][TEACHER_CNT+1];
        double BETA1[] = new double[POPULATION_SIZE+1];
        for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
            for (int tid = 1; tid < (TEACHER_CNT + 1); tid++) {

                teacherExtraPeriods[popIndex][tid] = M[popIndex][tid] - O[tid];
                if(teacherExtraPeriods[popIndex][tid]>BETA1[popIndex])
                    BETA1[popIndex] = teacherExtraPeriods[popIndex][tid];
            }
        }
        // SC5: 降低教師於上午四節的連續排課數量 - owner: chloe
        double U []= new double[POPULATION_SIZE + 1];
        double teacherCalsPeriods[][][]= new double[POPULATION_SIZE + 1][TEACHER_CNT + 1][DAY_CNT + 1];
        for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
            for (int tid = 1; tid < (TEACHER_CNT + 1); tid++) {
                for (int day = 1; day < (DAY_CNT + 1); day++) {

                    //降低上午四節的連續排課數量
                    for (int period = 1; period < 5 ; period++) {
                        teacherCalsPeriods[popIndex][tid][day]+= teacherActualTimetable[popIndex][tid][day][period];
                    }

                    if (teacherCalsPeriods[popIndex][tid][day] > U[popIndex])
                        U[popIndex] = teacherCalsPeriods[popIndex][tid][day];
                }

            }
        }
        // SC6: 降低教師下午三節的連續排課數量 - owner: chloe
        double V[] = new double[POPULATION_SIZE+1];
        double teacherCalsPeriods2[][][] = new double[POPULATION_SIZE + 1][TEACHER_CNT + 1][DAY_CNT + 1];
        for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
            for (int tid = 1; tid < (TEACHER_CNT + 1); tid++) {
                for (int day = 1; day < (DAY_CNT + 1); day++) {

                    //降低下午三節的連續排課數量
                    for (int period = 5; period <(PERIOD_CNT+1) ; period++) {
                        teacherCalsPeriods2[popIndex][tid][day]+= teacherActualTimetable[popIndex][tid][day][period];
                    }

                    if (teacherCalsPeriods2[popIndex][tid][day] > V[popIndex])
                        V[popIndex] = teacherCalsPeriods2[popIndex][tid][day];
                }

            }
        }
        // SC7: 降低教師午休時間前後的連續排課數量 - owner: chloe
        double  W[] = new double[POPULATION_SIZE+1];
        double teacherCalsPeriods3[][][] = new double[POPULATION_SIZE + 1][TEACHER_CNT + 1][DAY_CNT + 1];
        for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
            for (int tid = 1; tid < (TEACHER_CNT + 1); tid++) {
                for (int day = 1; day < (DAY_CNT + 1); day++) {

                    //降低午休前後的連續排課數量
                    for (int period = 4; period <5 ; period++) {
                        teacherCalsPeriods3[popIndex][tid][day]+= teacherActualTimetable[popIndex][tid][day][period];
                    }

                    if (teacherCalsPeriods3[popIndex][tid][day] > W[popIndex])
                        W[popIndex] = teacherCalsPeriods3[popIndex][tid][day];
                }

            }
        }

        // 加總上面的分數
        double totalFitness = 0.0;

        double alpha1 = 1;
        double alpha2 = 1;
        double alpha3 = 1;
        double alpha4 = 1;
        double alpha5 = 1;
        double alpha6 = 1;
        double alpha7 = 1;
        for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
            objectVal[popIndex] = alpha1 * fitnessJ[popIndex] + alpha2 * fitnessY[popIndex]+alpha3*BETA[popIndex]+alpha4*BETA1[popIndex]+alpha5*U[popIndex]+alpha6*V[popIndex]+alpha7*W[popIndex];
            // 適應值為目標值的倒數
            fitness[popIndex] = 1.0 / objectVal[popIndex];
            totalFitness += fitness[popIndex];
        }

        // 輸出初始解最佳的適應值
        double initial_bestFitnessVal = 0.0;
        int initial_bestPopIndex = 0;
        for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
            if (fitness[popIndex] > initial_bestFitnessVal) {
                initial_bestFitnessVal = fitness[popIndex];
                initial_bestPopIndex = popIndex;
            }
        }
        double initial_bestObjVal = 1 / initial_bestFitnessVal;
        if (DEBUG) {
            System.out.println("\tInitial Fitness: " + initial_bestFitnessVal + " (ObjVal: " + initial_bestObjVal + ")");
        }
        // 開始執行 GA 疊代
        int generation = 0;
        while (generation < MAX_GENERATIONS) {
            // 先照 fitness 值由大到小排序
            for (int popIndexA = 0; popIndexA < (POPULATION_SIZE + 1 - 1); popIndexA++) {
                int maxIdx = popIndexA;
                for (int popIndexB = (popIndexA + 1); popIndexB < (POPULATION_SIZE + 1); popIndexB++) {
                    if (fitness[popIndexB] > fitness[maxIdx]) {
                        maxIdx = popIndexB;
                    }
                }
                // 換 fitness 的順序
                double temp_fitness = fitness[maxIdx];
                fitness[maxIdx] = fitness[popIndexA];
                fitness[popIndexA] = temp_fitness;
                // 換 teacherActualTimetable
                int[][][] temp_teacherActualTimetable = Arrays.copyOf(teacherActualTimetable[maxIdx], teacherActualTimetable[maxIdx].length);
                teacherActualTimetable[maxIdx] = Arrays.copyOf(teacherActualTimetable[popIndexA], teacherActualTimetable[popIndexA].length);
                teacherActualTimetable[popIndexA] = Arrays.copyOf(temp_teacherActualTimetable, temp_teacherActualTimetable.length);
                // 換 classSubjectCnt
                int[][] temp_classSubjectCnt = Arrays.copyOf(classSubjectCnt[maxIdx], classSubjectCnt[maxIdx].length);
                classSubjectCnt[maxIdx] = Arrays.copyOf(classSubjectCnt[popIndexA], classSubjectCnt[popIndexA].length);
                classSubjectCnt[popIndexA] = Arrays.copyOf(temp_classSubjectCnt, temp_classSubjectCnt.length);
                // 換 subjectRoomCnt
                int[][][] temp_subjectRoomCnt = Arrays.copyOf(subjectRoomCnt[maxIdx], subjectRoomCnt[maxIdx].length);
                subjectRoomCnt[maxIdx] = Arrays.copyOf(subjectRoomCnt[popIndexA], subjectRoomCnt[popIndexA].length);
                subjectRoomCnt[popIndexA] = Arrays.copyOf(temp_subjectRoomCnt, temp_subjectRoomCnt.length);
                // 換 classSubjectTable
                int[][][] temp_classSubjectTable = Arrays.copyOf(classSubjectTable[maxIdx], classSubjectTable[maxIdx].length);
                classSubjectTable[maxIdx] = Arrays.copyOf(classSubjectTable[popIndexA], classSubjectTable[popIndexA].length);
                classSubjectTable[popIndexA] = Arrays.copyOf(temp_classSubjectTable, temp_classSubjectTable.length);
                // 換 classTeacherTable
                int[][][] temp_classTeacherTable = Arrays.copyOf(classTeacherTable[maxIdx], classTeacherTable[maxIdx].length);
                classTeacherTable[maxIdx] = Arrays.copyOf(classTeacherTable[popIndexA], classTeacherTable[popIndexA].length);
                classTeacherTable[popIndexA] = Arrays.copyOf(temp_classTeacherTable, temp_classTeacherTable.length);
                // 換 classAssignedTeacher
                int[][] temp_classAssignedTeacher = Arrays.copyOf(classAssignedTeacher[maxIdx], classAssignedTeacher[maxIdx].length);
                classAssignedTeacher[maxIdx] = Arrays.copyOf(classAssignedTeacher[popIndexA], classAssignedTeacher[popIndexA].length);
                classAssignedTeacher[popIndexA] = Arrays.copyOf(temp_classAssignedTeacher, temp_classAssignedTeacher.length);
            }

            if (DEBUG) {
                for (int i = 0; i < fitness.length; i++) {
                    System.out.println("\t\tfitness[" + i + "]: " + fitness[i] + " (ObjVal: " + 1 / fitness[i] + ")");
                }
            }

            int[][][][] children_teacherActualTimetable = new int[POPULATION_SIZE + 1][TEACHER_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
            int[][][] children_classSubjectCnt = new int[POPULATION_SIZE + 1][CLASS_CNT + 1][SUBJECT_CNT + 1];
            int[][][][] children_subjectRoomCnt = new int[POPULATION_SIZE + 1][SUBJECT_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
            int[][][][] children_classSubjectTable = new int[POPULATION_SIZE + 1][CLASS_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
            int[][][][] children_classTeacherTable = new int[POPULATION_SIZE + 1][CLASS_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
            int[][][] children_classAssignedTeacher = new int[POPULATION_SIZE + 1][CLASS_CNT + 1][SUBJECT_CNT + 1];

            // 先保留 elite size 的數量
            for (int popIndex = 0; popIndex < ELITISM_COUNT; popIndex++) {
                children_teacherActualTimetable[popIndex] = Arrays.copyOf(teacherActualTimetable[popIndex], teacherActualTimetable[popIndex].length);
                children_classSubjectCnt[popIndex] = Arrays.copyOf(classSubjectCnt[popIndex], classSubjectCnt[popIndex].length);
                children_subjectRoomCnt[popIndex] = Arrays.copyOf(subjectRoomCnt[popIndex], subjectRoomCnt[popIndex].length);
                children_classSubjectTable[popIndex] = Arrays.copyOf(classSubjectTable[popIndex], classSubjectTable[popIndex].length);
                children_classTeacherTable[popIndex] = Arrays.copyOf(classTeacherTable[popIndex], classTeacherTable[popIndex].length);
                children_classAssignedTeacher[popIndex] = Arrays.copyOf(classAssignedTeacher[popIndex], classAssignedTeacher[popIndex].length);
            }

            int pidx = ELITISM_COUNT;
            while (pidx < (POPULATION_SIZE + 1)) {

                if (DEBUG) {
                    System.out.println("Running crossover...");
                }

                // 以輪盤法選擇交配使用的 2 個父代的 population
                double[] cumulative_sum = new double[POPULATION_SIZE + 1];
                double[] cumulative_prec = new double[POPULATION_SIZE + 1];
                for (int i = 0; i < (POPULATION_SIZE + 1); i++) {
                    if (i == 0) {
                        cumulative_sum[i] = fitness[i];
                    }
                    else {
                        cumulative_sum[i] = cumulative_sum[i - 1] + fitness[i];
                    }
                    cumulative_prec[i] = cumulative_sum[i] / totalFitness * 100;
                }

                int parentIndex1 = 0, parentIndex2 = 0;
                Random r = new Random();
                // range: 1 - 100
                int randomInt1 = r.nextInt(100) + 1;
                for (int i = 0; i < (POPULATION_SIZE + 1); i++) {
                    if (randomInt1 <= cumulative_prec[i]) {
                        parentIndex1 = i;
                        break;
                    }
                }
                int randomInt2 = r.nextInt(100) + 1;
                while (randomInt2 == randomInt1) {
                    randomInt2 = r.nextInt(100) + 1;
                }
                for (int i = 0; i < (POPULATION_SIZE + 1); i++) {
                    if (randomInt2 <= cumulative_prec[i]) {
                        parentIndex2 = i;
                        break;
                    }
                }

                // 2 個父代的 population 交配
                // 選擇一個要拿來交配的班級候選號碼
                int selectedClassId = r.nextInt(CLASS_CNT) + 1;
//                System.out.println("selectedClassId: " + selectedClassId);
                // 決定這個班級是否要交配
                // 被選中的班級要交配
                if (CROSSOVER_RATE > (Math.random())) {
//                    System.out.println("\tCrossover population " + pidx);
                    // 先複製其他不用交配的子代
                    // 老師可以的時間以 parentIndex1 為主
                    // 先複製要交配以外的班級，複製 parentIndex1 的課程、任課老師、教室
                    for (int cidx = 1; cidx < (CLASS_CNT + 1); cidx++) {
                        if (cidx == selectedClassId) {
                            continue;
                        }
                        for (int s = 1; s < (SUBJECT_CNT + 1); s++) {
                            children_classSubjectCnt[pidx][cidx][s] = classSubjectCnt[parentIndex1][cidx][s];
                            children_classAssignedTeacher[pidx][cidx][s] = classAssignedTeacher[parentIndex1][cidx][s];
                        }
                    }
                    for (int cidx = 1; cidx < CLASS_CNT + 1; cidx++) {
                        if (cidx == selectedClassId) {
                            continue;
                        }
                        for (int d = 1; d < (DAY_CNT + 1); d++) {
                            for (int p = 1; p < (PERIOD_CNT + 1); p++) {
                                children_classSubjectTable[pidx][cidx][d][p] = classSubjectTable[parentIndex1][cidx][d][p];
                                children_classTeacherTable[pidx][cidx][d][p] = classTeacherTable[parentIndex1][cidx][d][p];
                                int sidx = classSubjectTable[parentIndex1][cidx][d][p];
                                children_subjectRoomCnt[pidx][sidx][d][p]++;
                                int tidx = classTeacherTable[parentIndex1][cidx][d][p];
                                children_teacherActualTimetable[pidx][tidx][d][p]++;
                            }
                        }
                    }
                    // 開始處理被選中要交配的班級
                    // 固定課程 & 體育課照 parentIndex1
                    // 班會、周會、聯課、體育課
                    int classMeetingId = 22;
                    int classMeetingDay = 1;
                    int classMeetingPeriod = 1;
                    int schoolMeetingId = 20;
                    int schoolMeetingDay = 2;
                    int schoolMeetingPeriod = 3;
                    int clubCourseId = 21;
                    int clubCourseDay = 2;
                    int clubCoursePeriod = 4;
                    int peId = 13;
                    int peDay = 0;
                    int pePeriod = 0;
                    for (int subjectIndex = 1; subjectIndex < (SUBJECT_CNT + 1); subjectIndex++) {
                        if (subjectIndex == classMeetingId) {
                            children_classSubjectTable[pidx][selectedClassId][classMeetingDay][classMeetingPeriod] = classMeetingId;
                            children_classSubjectCnt[pidx][selectedClassId][classMeetingId]++;
                            int tid = classAssignedTeacher[parentIndex1][selectedClassId][classMeetingId];
                            children_classAssignedTeacher[pidx][selectedClassId][classMeetingId] = tid;
                            children_classTeacherTable[pidx][selectedClassId][classMeetingDay][classMeetingPeriod] = tid;
                            children_teacherActualTimetable[pidx][tid][classMeetingDay][classMeetingPeriod]++;
                            children_subjectRoomCnt[pidx][classMeetingId][classMeetingDay][classMeetingPeriod]++;
                        }
                        else if (subjectIndex == schoolMeetingId) {
                            children_classSubjectTable[pidx][selectedClassId][schoolMeetingDay][schoolMeetingPeriod] = schoolMeetingId;
                            children_classSubjectCnt[pidx][selectedClassId][schoolMeetingId]++;
                            int tid = classAssignedTeacher[parentIndex1][selectedClassId][schoolMeetingId];
                            children_classAssignedTeacher[pidx][selectedClassId][schoolMeetingId] = tid;
                            children_classTeacherTable[pidx][selectedClassId][schoolMeetingDay][schoolMeetingPeriod] = tid;
                            children_teacherActualTimetable[pidx][tid][schoolMeetingDay][schoolMeetingPeriod]++;
                            children_subjectRoomCnt[pidx][schoolMeetingId][schoolMeetingDay][schoolMeetingPeriod]++;
                        }
                        else if (subjectIndex == clubCourseId) {
                            children_classSubjectTable[pidx][selectedClassId][clubCourseDay][clubCoursePeriod] = clubCourseId;
                            children_classSubjectCnt[pidx][selectedClassId][clubCourseId]++;
                            int tid = classAssignedTeacher[parentIndex1][selectedClassId][clubCourseId];
                            children_classAssignedTeacher[pidx][selectedClassId][clubCourseId] = tid;
                            children_classTeacherTable[pidx][selectedClassId][clubCourseDay][clubCoursePeriod] = tid;
                            children_teacherActualTimetable[pidx][tid][clubCourseDay][clubCoursePeriod]++;
                            children_subjectRoomCnt[pidx][clubCourseId][clubCourseDay][clubCoursePeriod]++;
                        }
                        else if (subjectIndex == peId) {
                            // 找 parentIndex1 第一節體育課的時間
                            for (int d = 1; d < (DAY_CNT + 1); d++) {
                                for (int p = 1; p < (PERIOD_CNT + 1); p++) {
                                    if (classSubjectTable[parentIndex1][selectedClassId][d][p] == peId) {
                                        if (classSubjectTable[parentIndex1][selectedClassId][d][p + 1] == peId) {
                                            peDay = d;
                                            pePeriod = p;
                                            break;
                                        }
                                    }
                                }
                            }
                            children_classSubjectTable[pidx][selectedClassId][peDay][pePeriod] = peId;
                            children_classSubjectTable[pidx][selectedClassId][peDay][pePeriod + 1] = peId;
                            children_classSubjectCnt[pidx][selectedClassId][clubCourseId] += 2;
                            int tid = classAssignedTeacher[parentIndex1][selectedClassId][peId];
                            children_classAssignedTeacher[pidx][selectedClassId][peId] = tid;
                            children_classTeacherTable[pidx][selectedClassId][peDay][pePeriod] = tid;
                            children_classTeacherTable[pidx][selectedClassId][peDay][pePeriod + 1] = tid;
                            children_teacherActualTimetable[pidx][tid][peDay][pePeriod]++;
                            children_teacherActualTimetable[pidx][tid][peDay][pePeriod + 1]++;
                            children_subjectRoomCnt[pidx][peId][peDay][pePeriod]++;
                            children_subjectRoomCnt[pidx][peId][peDay][pePeriod + 1]++;
                        }
                    }
                    // 安排被選中的班級班會、周會、聯課、體育課以外的單堂課程
                    int selectedClassFailureCnt = 0;
                    boolean isArrangingSelectedClass = true;
                    REARRANGE_SELECTED_CLASS:
                    while (isArrangingSelectedClass) {
                        boolean arrangeSelectedClassAgain = false;
                        int[][][] temp1_teacherActualTimetable = new int[TEACHER_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
                        int[][] temp1_classSubjectCnt = new int [CLASS_CNT + 1][SUBJECT_CNT + 1];
                        int[][][] temp1_subjectRoomCnt = new int[SUBJECT_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
                        int[][][] temp1_classSubjectTable = new int[CLASS_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
                        int[][][] temp1_classTeacherTable = new int[CLASS_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
                        int[][] temp1_classAssignedTeacher = new int[CLASS_CNT + 1][SUBJECT_CNT + 1];
                        // 複製前面被選定的班級的固定課程 & 體育課的陣列
                        for (int classIndex = 1; classIndex < (CLASS_CNT + 1); classIndex++) {
                            for (int t = 0; t < (TEACHER_CNT + 1); t++) {
                                for (int d = 0; d < (DAY_CNT + 1); d++) {
                                    for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                                        temp1_teacherActualTimetable[t][d][p] = children_teacherActualTimetable[pidx][t][d][p];
                                    }
                                }
                            }
                            for (int c = 0; c < (CLASS_CNT + 1); c++) {
                                for (int s = 0; s < (SUBJECT_CNT + 1); s++) {
                                    temp1_classSubjectCnt[c][s] = children_classSubjectCnt[pidx][c][s];
                                    temp1_classAssignedTeacher[c][s] = children_classAssignedTeacher[pidx][c][s];
                                }
                            }
                            for (int s = 0; s < (SUBJECT_CNT + 1); s++) {
                                for (int d = 0; d < (DAY_CNT + 1); d++) {
                                    for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                                        temp1_subjectRoomCnt[s][d][p] = children_subjectRoomCnt[pidx][s][d][p];
                                    }
                                }
                            }
                            for (int c = 0; c < (CLASS_CNT + 1); c++) {
                                for (int d = 0; d < (DAY_CNT + 1); d++) {
                                    for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                                        temp1_classSubjectTable[c][d][p] = children_classSubjectTable[pidx][c][d][p];
                                        temp1_classTeacherTable[c][d][p] = children_classTeacherTable[pidx][c][d][p];
                                    }
                                }
                            }
                        }

                        for (int d = 1; d < (DAY_CNT + 1); d++) {
                            for (int p = 1; p < (PERIOD_CNT + 1); p++) {
                                // 略過班會、周會、聯課、體育課
                                int subjectIndex = temp1_classSubjectTable[selectedClassId][d][p];
                                if (subjectIndex == classMeetingId || subjectIndex == schoolMeetingId ||
                                        subjectIndex == clubCourseId || subjectIndex == peId) {
                                    continue ;
                                }
                                else if (subjectIndex != 0) {
                                    System.out.println("Error: pidx " + pidx + " select " + selectedClassId + " d " + d + " p " + p + " is not null.");
                                }
                                // 檢查其他每一節課 parent1 & parent2 是否交換
                                // 如果已經填滿就跳過
                                if (0.5 > (Math.random())) {
                                    subjectIndex = classSubjectTable[parentIndex2][selectedClassId][d][p];
                                    int tid = classAssignedTeacher[parentIndex1][selectedClassId][subjectIndex];
                                    boolean tooMany, noEmptyRoom, invalidForTeacher;
                                    tooMany = temp1_classSubjectCnt[selectedClassId][subjectIndex] >= subjectFixedCnt[gradeOfClass[selectedClassId]][subjectIndex];
                                    noEmptyRoom = temp1_subjectRoomCnt[subjectIndex][d][p] >= subjectRoomLimit[subjectIndex];
                                    invalidForTeacher = temp1_teacherActualTimetable[tid][d][p] != 0;
                                    // 該課程節數已排滿，跳過
                                    if (tooMany) {
//                                        System.out.println("Skip d " + d + " p " + p + " subjectIndex " + subjectIndex + " 's classes are too many");
                                        // 記錄該課程的時間
                                        continue;
                                    }
                                    else if (noEmptyRoom || invalidForTeacher) {
                                        // 無法交換，用 parent1 的
                                        subjectIndex = classSubjectTable[parentIndex1][selectedClassId][d][p];
                                        tid = classTeacherTable[parentIndex1][selectedClassId][d][p];
                                        // 記錄該課程的時間
                                        temp1_classSubjectTable[selectedClassId][d][p] = subjectIndex;
                                        temp1_classSubjectCnt[selectedClassId][subjectIndex]++;
                                        // 記錄該課程的老師
                                        if (temp1_classAssignedTeacher[selectedClassId][subjectIndex] == 0) {
                                            temp1_classAssignedTeacher[selectedClassId][subjectIndex] = tid;
                                        }
                                        temp1_classTeacherTable[selectedClassId][d][p] = tid;
                                        temp1_teacherActualTimetable[tid][d][p]++;
                                        // 記錄該課程的教室
                                        temp1_subjectRoomCnt[subjectIndex][d][p]++;
                                    }
                                    // 該節課交換
                                    else {
                                        // System.out.println("Turn d " + d + " p " + p + " 's subject " + classSubjectTable[parentIndex1][selectedClassId][d][p] + " to " + subjectIndex);
                                        // 記錄該課程的時間
                                        temp1_classSubjectTable[selectedClassId][d][p] = subjectIndex;
                                        temp1_classSubjectCnt[selectedClassId][subjectIndex]++;
                                        // 記錄該課程的老師
                                        if (temp1_classAssignedTeacher[selectedClassId][subjectIndex] == 0) {
                                            temp1_classAssignedTeacher[selectedClassId][subjectIndex] = tid;
                                        }
                                        temp1_classTeacherTable[selectedClassId][d][p] = tid;
                                        temp1_teacherActualTimetable[tid][d][p]++;
                                        // 記錄該課程的教室
                                        temp1_subjectRoomCnt[subjectIndex][d][p]++;
                                    }
                                }
                                // 如果該節課沒有交換，用 parent1 的
                                else {
                                    subjectIndex = classSubjectTable[parentIndex1][selectedClassId][d][p];
                                    int tid = classTeacherTable[parentIndex1][selectedClassId][d][p];
                                    // 記錄該課程的時間
                                    temp1_classSubjectTable[selectedClassId][d][p] = subjectIndex;
                                    temp1_classSubjectCnt[selectedClassId][subjectIndex]++;
                                    // 記錄該課程的老師
                                    if (temp1_classAssignedTeacher[selectedClassId][subjectIndex] == 0) {
                                        temp1_classAssignedTeacher[selectedClassId][subjectIndex] = tid;
                                    }
                                    temp1_classTeacherTable[selectedClassId][d][p] = tid;
                                    temp1_teacherActualTimetable[tid][d][p]++;
                                    // 記錄該課程的教室
                                    temp1_subjectRoomCnt[subjectIndex][d][p]++;
                                }
                            }
                        }

                        // 交配完後，修補被選中要交配的班級的課表 (用上面建立初始解的方法)
                        // 如果發現填不滿，就重新跑跑看
                        for (int subjectIndex = 1; subjectIndex < (SUBJECT_CNT + 1); subjectIndex++) {
                            // 略過班會、周會、聯課、體育課
                            if (subjectIndex == classMeetingId || subjectIndex == schoolMeetingId ||
                                    subjectIndex == clubCourseId || subjectIndex == peId) {
                                continue ;
                            }

                            int tempClassFailureCnt = 0;
                            boolean isArrangingTempClass = true;
                            REARRANGE_TEMP_CLASS:
                            while (isArrangingTempClass) {
                                int[][][] temp2_teacherActualTimetable = new int[TEACHER_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
                                int[][] temp2_classSubjectCnt = new int [CLASS_CNT + 1][SUBJECT_CNT + 1];
                                int[][][] temp2_subjectRoomCnt = new int[SUBJECT_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
                                int[][][] temp2_classSubjectTable = new int[CLASS_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
                                int[][][] temp2_classTeacherTable = new int[CLASS_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
                                int[][] temp2_classAssignedTeacher = new int[CLASS_CNT + 1][SUBJECT_CNT + 1];
                                // 複製 temp1 的陣列
                                for (int t = 0; t < (TEACHER_CNT + 1); t++) {
                                    for (int d = 0; d < (DAY_CNT + 1); d++) {
                                        for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                                            temp2_teacherActualTimetable[t][d][p] = temp1_teacherActualTimetable[t][d][p];
                                        }
                                    }
                                }
                                for (int c = 0; c < (CLASS_CNT + 1); c++) {
                                    for (int s = 0; s < (SUBJECT_CNT + 1); s++) {
                                        temp2_classSubjectCnt[c][s] = temp1_classSubjectCnt[c][s];
                                        temp2_classAssignedTeacher[c][s] = temp1_classAssignedTeacher[c][s];
                                    }
                                }
                                for (int s = 0; s < (SUBJECT_CNT + 1); s++) {
                                    for (int d = 0; d < (DAY_CNT + 1); d++) {
                                        for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                                            temp2_subjectRoomCnt[s][d][p] = temp1_subjectRoomCnt[s][d][p];
                                        }
                                    }
                                }
                                for (int c = 0; c < (CLASS_CNT + 1); c++) {
                                    for (int d = 0; d < (DAY_CNT + 1); d++) {
                                        for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                                            temp2_classSubjectTable[c][d][p] = temp1_classSubjectTable[c][d][p];
                                            temp2_classTeacherTable[c][d][p] = temp1_classTeacherTable[c][d][p];
                                        }
                                    }
                                }

                                boolean arrangeTempClassAgain = false;

                                for (int classIndex = 1; classIndex < (CLASS_CNT + 1); classIndex++) {

                                    // 略過班會、周會、聯課、體育課
                                    if (subjectIndex == classMeetingId || subjectIndex == schoolMeetingId ||
                                            subjectIndex == clubCourseId || subjectIndex == peId) {
                                        continue ;
                                    }

                                    while (temp2_classSubjectCnt[classIndex][subjectIndex] < subjectFixedCnt[gradeOfClass[classIndex]][subjectIndex]) {
                                        // 重排條件 1
                                        if (selectedClassFailureCnt > 1000) {
//                                            System.out.println("\tJump to REARRANGE_SELECTED_CLASS");
                                            arrangeSelectedClassAgain = true;
                                            selectedClassFailureCnt = 0;
                                            break;
                                        }

                                        // 選單堂課程的老師
                                        int teacherId = 0;
                                        ArrayList<Integer> randomNumberList = new ArrayList<>();
                                        for (int i = 1; i <= TEACHER_CNT; i++) {
                                            randomNumberList.add(i);
                                        }
                                        Collections.shuffle(randomNumberList);
                                        int listIndex = 0;
                                        do {
                                            if (temp2_classAssignedTeacher[classIndex][subjectIndex] == 0) {
                                                // 如果沒有老師能配合該班級上課
                                                if (listIndex >= randomNumberList.size()) {
                                                    arrangeTempClassAgain = true;
                                                    tempClassFailureCnt++;
                                                    break;
                                                }
                                                teacherId = randomNumberList.get(listIndex);
                                                listIndex++;
                                            }
                                            else {
                                                teacherId = temp2_classAssignedTeacher[classIndex][subjectIndex];
                                            }
                                        } while(teacherTeaching[teacherId][subjectIndex] == 0);
                                        randomNumberList.clear();
                                        if (arrangeTempClassAgain) {
//                                            System.out.print("\tUnknown Error: ");
//                                            System.out.println("Jump to REARRANGE_TEMP_CLASS when arrange class " + classIndex + "'s teacher for subject " + subjectIndex);
                                            break;
                                        }

                                        // 選單堂課程的時間 (要班級 & 老師皆可以的時間)
                                        int day = 0;
                                        int period = 0;
                                        boolean noEmptyRoom, invalidForClass, invalidForTeacher;
                                        do {

                                            if (day != 0 && period != 0) {
                                                tempClassFailureCnt++;
                                            }
                                            // 如果持續找不到班級&老師能配對的時間，重新安排該班級的課程
                                            if (tempClassFailureCnt > 3600) {
                                                arrangeTempClassAgain = true;
                                                selectedClassFailureCnt++;
                                                tempClassFailureCnt = 0;
                                                break;
                                            }

                                            day = ThreadLocalRandom.current().nextInt(1, DAY_CNT + 1);
                                            period = ThreadLocalRandom.current().nextInt(1, PERIOD_CNT + 1);
                                            noEmptyRoom = temp2_subjectRoomCnt[subjectIndex][day][period] >= subjectRoomLimit[subjectIndex];
                                            invalidForClass = temp2_classSubjectTable[classIndex][day][period] != 0;
                                            invalidForTeacher = temp2_teacherActualTimetable[teacherId][day][period] != 0;

                                        } while (noEmptyRoom || invalidForClass || invalidForTeacher);
                                        if (arrangeTempClassAgain) {
//                                            System.out.println("\tJump to REARRANGE_TEMP_CLASS when arrange class " + classIndex + "'s time for subject " +  subjectIndex);
                                            break;
                                        }

                                        // 記錄該課程的時間
                                        temp2_classSubjectTable[classIndex][day][period] = subjectIndex;
                                        temp2_classSubjectCnt[classIndex][subjectIndex]++;
                                        // 記錄該課程的老師
                                        if (temp2_classAssignedTeacher[classIndex][subjectIndex] == 0) {
                                            temp2_classAssignedTeacher[classIndex][subjectIndex] = teacherId;
                                        }
                                        temp2_classTeacherTable[classIndex][day][period] = teacherId;
                                        temp2_teacherActualTimetable[teacherId][day][period]++;
                                        // 記錄該課程的教室
                                        temp2_subjectRoomCnt[subjectIndex][day][period]++;
                                    }

                                    if (arrangeTempClassAgain) {
                                        break;
                                    }
                                    if (arrangeSelectedClassAgain) {
                                        break;
                                    }
                                }

                                if (arrangeSelectedClassAgain) {
                                    break;
                                }
                                if (arrangeTempClassAgain) {
                                    continue REARRANGE_TEMP_CLASS;
                                }

                                isArrangingTempClass = false;

                                // 確認該班級排課完畢才將 temp2 複製到 temp1 的陣列
                                for (int t = 0; t < (TEACHER_CNT + 1); t++) {
                                    for (int d = 0; d < (DAY_CNT + 1); d++) {
                                        for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                                            temp1_teacherActualTimetable[t][d][p] = temp2_teacherActualTimetable[t][d][p];
                                        }
                                    }
                                }
                                for (int c = 0; c < (CLASS_CNT + 1); c++) {
                                    for (int s = 0; s < (SUBJECT_CNT + 1); s++) {
                                        temp1_classSubjectCnt[c][s] = temp2_classSubjectCnt[c][s];
                                        temp1_classAssignedTeacher[c][s] = temp2_classAssignedTeacher[c][s];
                                    }
                                }
                                for (int s = 0; s < (SUBJECT_CNT + 1); s++) {
                                    for (int d = 0; d < (DAY_CNT + 1); d++) {
                                        for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                                            temp1_subjectRoomCnt[s][d][p] = temp2_subjectRoomCnt[s][d][p];
                                        }
                                    }
                                }
                                for (int c = 0; c < (CLASS_CNT + 1); c++) {
                                    for (int d = 0; d < (DAY_CNT + 1); d++) {
                                        for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                                            temp1_classSubjectTable[c][d][p] = temp2_classSubjectTable[c][d][p];
                                            temp1_classTeacherTable[c][d][p] = temp2_classTeacherTable[c][d][p];
                                        }
                                    }
                                }
                            }

                            if (arrangeSelectedClassAgain) {
                                break;
                            }
                        }

                        if (arrangeSelectedClassAgain) {
                            continue REARRANGE_SELECTED_CLASS;
                        }

                        isArrangingSelectedClass = false;

                        // 確認被選中的班級交配完畢才複製到 children 的陣列
                        for (int t = 0; t < (TEACHER_CNT + 1); t++) {
                            for (int d = 0; d < (DAY_CNT + 1); d++) {
                                for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                                    children_teacherActualTimetable[pidx][t][d][p] = temp1_teacherActualTimetable[t][d][p];
                                }
                            }
                        }
                        for (int c = 0; c < (CLASS_CNT + 1); c++) {
                            for (int s = 0; s < (SUBJECT_CNT + 1); s++) {
                                children_classSubjectCnt[pidx][c][s] = temp1_classSubjectCnt[c][s];
                                children_classAssignedTeacher[pidx][c][s] = temp1_classAssignedTeacher[c][s];
                            }
                        }
                        for (int s = 0; s < (SUBJECT_CNT + 1); s++) {
                            for (int d = 0; d < (DAY_CNT + 1); d++) {
                                for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                                    children_subjectRoomCnt[pidx][s][d][p] = temp1_subjectRoomCnt[s][d][p];
                                }
                            }
                        }
                        for (int c = 0; c < (CLASS_CNT + 1); c++) {
                            for (int d = 0; d < (DAY_CNT + 1); d++) {
                                for (int p = 0; p < (PERIOD_CNT + 1); p++) {
                                    children_classSubjectTable[pidx][c][d][p] = temp1_classSubjectTable[c][d][p];
                                    children_classTeacherTable[pidx][c][d][p] = temp1_classTeacherTable[c][d][p];
                                }
                            }
                        }

                    }
                }
                // 被選中的班級不用交配
                else {
//                    System.out.println("\tDon't crossover population " + pidx);
                    for (int cidx = 1; cidx < (CLASS_CNT + 1); cidx++) {
                        for (int s = 1; s < (SUBJECT_CNT + 1); s++) {
                            children_classSubjectCnt[pidx][cidx][s] = classSubjectCnt[parentIndex1][cidx][s];
                            children_classAssignedTeacher[pidx][cidx][s] = classAssignedTeacher[parentIndex1][cidx][s];
                        }
                    }
                    for (int cidx = 1; cidx < CLASS_CNT + 1; cidx++) {
                        for (int d = 1; d < (DAY_CNT + 1); d++) {
                            for (int p = 1; p < (PERIOD_CNT + 1); p++) {
                                children_classSubjectTable[pidx][cidx][d][p] = classSubjectTable[parentIndex1][cidx][d][p];
                                children_classTeacherTable[pidx][cidx][d][p] = classTeacherTable[parentIndex1][cidx][d][p];
                                int sidx = classSubjectTable[parentIndex1][cidx][d][p];
                                children_subjectRoomCnt[pidx][sidx][d][p]++;
                                int tidx = classTeacherTable[parentIndex1][cidx][d][p];
                                children_teacherActualTimetable[pidx][tidx][d][p]++;
                            }
                        }
                    }
                }

                if (DEBUG) {
                    System.out.println("Running mutation...");
                }

                // TODO: 交配完的子代進行突變
                // Step 1 : 先設定突變率Pm，再隨機產生0 至1 的數值，若此值小於突變率，會進行突變
                // temp表格
                int [][] m_classSubjectTable = new int[POPULATION_SIZE+1][(DAY_CNT + 1) * (PERIOD_CNT + 1)];
                int [][] m_classTeacherTable = new int[POPULATION_SIZE+1][(DAY_CNT + 1) * (PERIOD_CNT + 1)];

                int [][] temp_classSubjectTable = new int[POPULATION_SIZE+1][(DAY_CNT + 1) * (PERIOD_CNT + 1)];
                int [][] temp_classTeacherTable = new int[POPULATION_SIZE+1][(DAY_CNT + 1) * (PERIOD_CNT + 1)];
                int selectedMClassId;

                for(int mpop = pidx ; mpop < pidx+1 ; mpop++)//checkbox :更改這個參數 mpop < pidx+1
                {
                    if (Math.random() <= MUTATION_RATE) {
                        int out = 0;
                        do {
                            //隨機選取欲變異的班級
                            selectedMClassId = r.nextInt(CLASS_CNT) + 1;

                            //把內容寫到新的課表裡面
                            int countM = 0;
                            for (int mday = 1; mday < (DAY_CNT + 1); mday++) {
                                for (int mperiod = 1; mperiod < (PERIOD_CNT + 1); mperiod++) {
                                    m_classSubjectTable[mpop][countM] = children_classSubjectTable[mpop][selectedMClassId][mday][mperiod];
                                    m_classTeacherTable[mpop][countM] = children_classTeacherTable[mpop][selectedMClassId][mday][mperiod];
                                    temp_classSubjectTable[mpop][countM] = children_classSubjectTable[mpop][selectedMClassId][mday][mperiod];
                                    temp_classTeacherTable[mpop][countM] = children_classTeacherTable[mpop][selectedMClassId][mday][mperiod];
                                    countM++;
                                }
                            }


                            //隨機選取兩個突變點(節)
                            int mp1;
                            int mp2;
                            do {
                                mp1 = r.nextInt(SUBJECT_CNT) + 1;
                            } while ( mp1 == 9 || mp1 == 10);

                            //Step 2-1-1:當突變點為連堂課程會一次交換兩節課(m1 & m1+1)
                            if (m_classSubjectTable[mpop][mp1] == 13 && m_classSubjectTable[mpop][mp1+1] == 13) {
                                for (int classIndex1 = 0; classIndex1 < (DAY_CNT * PERIOD_CNT); classIndex1++) {
                                    if (children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp1 / 7][mp1 % 7]][classIndex1 / 7][classIndex1 % 7] == 1 &&
                                            children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][(mp1 + 1) / 7][(mp1 + 1) % 7]][(classIndex1 + 1) / 7][(classIndex1 + 1) % 7] == 1) {
                                        mp2 = classIndex1;
                                        //HC6,8,12,13,14: mp2 不可以是班、周會或聯課 & mp1!=mp2 & mp2不可以是每天第四節或是最後一節課 & 不用換相同科目的
                                        if (mp2 != 0 && mp2 != 9 && mp2 != 10 && mp1 != mp2 && mp2 / 7 != 3 && mp2 / 7 != 6 && m_classSubjectTable[mpop][mp1] != m_classSubjectTable[mpop][mp2]) {
                                            if (children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp2 / 7][mp2 % 7]][mp1 / 7][mp1 % 7] == 1 &&
                                                    children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][(mp2 + 1) / 7][(mp2 + 1) % 7]][(mp1 + 1) / 7][(mp1 + 1) % 7] == 1) {
                                                out = 1;
                                                m_classSubjectTable[mpop][mp1] = m_classSubjectTable[mpop][mp2];
                                                m_classSubjectTable[mpop][mp1 + 1] = m_classSubjectTable[mpop][mp2 + 1];
                                                m_classSubjectTable[mpop][mp2] = temp_classSubjectTable[mpop][mp1];
                                                m_classSubjectTable[mpop][mp2 + 1] = temp_classSubjectTable[mpop][mp1 + 1];
                                                m_classTeacherTable[mpop][mp1] = temp_classTeacherTable[mpop][mp2];
                                                m_classTeacherTable[mpop][mp2] = temp_classTeacherTable[mpop][mp1];
                                                m_classTeacherTable[mpop][mp1 + 1] = m_classTeacherTable[mpop][mp2 + 1];
                                                m_classTeacherTable[mpop][mp2 + 1] = temp_classTeacherTable[mpop][mp1 + 1];

                                                children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp1 / 7][mp1 % 7]][mp1 / 7][mp1 % 7]++;
                                                children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp2 / 7][mp2 % 7]][mp2 / 7][mp2 % 7]++;
                                                children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][(mp1+1) / 7][(mp1+1) % 7]][(mp1+1) / 7][(mp1+1) % 7]++;
                                                children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][(mp2+1) / 7][(mp2+1) % 7]][(mp2+1) / 7][(mp2+1) % 7]++;
                                                children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp1 / 7][mp1 % 7]][mp2 / 7][mp2 % 7]--;
                                                children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][(mp1+1) / 7][(mp1+1) % 7]][(mp2+1) / 7][(mp2+1) % 7]--;
                                                children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp2 / 7][mp2 % 7]][mp1 / 7][mp1 % 7]--;
                                                children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][(mp2+1) / 7][(mp2+1) % 7]][(mp1+1) / 7][(mp1+1) % 7]--;
                                                break;
                                            }else
                                                out = 0;
                                        }

                                    }else
                                        out = 0;
                                }
                            }

                            //Step 2-1-2:當突變點為連堂課程會一次交換兩節課(m1 & m1-1)
                            if (m_classSubjectTable[mpop][mp1] == 13 && m_classSubjectTable[mpop][mp1-1] == 13) {
                                for (int classIndex2 = (DAY_CNT * PERIOD_CNT) - 1; classIndex2 > -1; classIndex2--) {
                                    if (children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp1 / 7][mp1 % 7]][classIndex2 / 7][classIndex2 % 7] == 1 &&
                                            children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][(mp1 - 1) / 7][(mp1 - 1) % 7]][(classIndex2 - 1) / 7][(classIndex2 - 1) % 7] == 1) {
                                        mp2 = classIndex2;

                                        //HC6,8,12,13,14: mp2 不可以是班、周會或聯課 & mp1!=mp2 & mp2不可以是每天第四節或是最後一節課 & 不用換相同科目的
                                        if (mp2 != 9 && mp2 != 10 && mp1 != mp2 && mp2 / 7 != 4 && mp2 / 7 != 0 && m_classSubjectTable[mpop][mp1] != m_classSubjectTable[mpop][mp2]) {
                                            if (children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp2 / 7][mp2 % 7]][mp1 / 7][mp1 % 7] == 1 &&
                                                    children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][(mp2 - 1) / 7][(mp2 - 1) % 7]][(mp1 - 1) / 7][(mp1 - 1) % 7] == 1) {
                                                out = 1;
                                                m_classSubjectTable[mpop][mp1] = m_classSubjectTable[mpop][mp2];
                                                m_classSubjectTable[mpop][mp1 - 1] = m_classSubjectTable[mpop][mp2 - 1];
                                                m_classSubjectTable[mpop][mp2] = temp_classSubjectTable[mpop][mp1];
                                                m_classSubjectTable[mpop][mp2 - 1] = temp_classSubjectTable[mpop][mp1 - 1];
                                                m_classTeacherTable[mpop][mp1] = temp_classTeacherTable[mpop][mp2];
                                                m_classTeacherTable[mpop][mp2] = temp_classTeacherTable[mpop][mp1];
                                                m_classTeacherTable[mpop][mp1 - 1] = m_classTeacherTable[mpop][mp2 - 1];
                                                m_classTeacherTable[mpop][mp2 - 1] = temp_classTeacherTable[mpop][mp1 - 1];

                                                children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp1 / 7][mp1 % 7]][mp1 / 7][mp1 % 7]++;
                                                children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp2 / 7][mp2 % 7]][mp2 / 7][mp2 % 7]++;
                                                children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][(mp1 - 1) / 7][(mp1 - 1) % 7]][(mp1 - 1) / 7][(mp1 - 1) % 7]++;
                                                children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][(mp2 - 1) / 7][(mp2 - 1) % 7]][(mp2 - 1) / 7][(mp2 - 1) % 7]++;
                                                children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp1 / 7][mp1 % 7]][mp2 / 7][mp2 % 7]--;
                                                children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][(mp1 - 1) / 7][(mp1 - 1) % 7]][(mp2 - 1) / 7][(mp2 - 1) % 7]--;
                                                children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp2 / 7][mp2 % 7]][mp1 / 7][mp1 % 7]--;
                                                children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][(mp2 - 1) / 7][(mp2 - 1) % 7]][(mp1 - 1) / 7][(mp1 - 1) % 7]--;
                                                break;
                                            }

                                        } else
                                            out = 0;
                                    }

                                }
                            }
                            //Step 2-2: 而單堂課程為一次交換一節課
                            if (m_classSubjectTable[mpop][mp1] != 13) {
                                if(Math.random()*100 %10 <5) {
                                    for (int periodIndex = 0; periodIndex < (DAY_CNT * PERIOD_CNT); periodIndex++) {

                                        if (children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp1 / 7][mp1 % 7]][periodIndex / 7][periodIndex % 7] == 1) {
                                            mp2 = periodIndex;
                                            //HC6,8,12,13,14: mp2 不可以是班、周會或聯課 & mp1!=mp2 & 不用換相同科目的
                                            if (mp2 != 0 && mp2 != 9 && mp2 != 10 && m_classSubjectTable[mpop][mp1] != m_classSubjectTable[mpop][mp2]) {
                                                if (children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp2 / 7][mp2 % 7]][mp1 / 7][mp1 % 7] == 1) {

                                                    out = 1;
                                                    m_classSubjectTable[mpop][mp1] = m_classSubjectTable[mpop][mp2];
                                                    m_classSubjectTable[mpop][mp2] = temp_classSubjectTable[mpop][mp1];
                                                    m_classTeacherTable[mpop][mp1] = temp_classTeacherTable[mpop][mp2];
                                                    m_classTeacherTable[mpop][mp2] = temp_classTeacherTable[mpop][mp1];

                                                    children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp1 / 7][mp1 % 7]][mp1 / 7][mp1 % 7]++;
                                                    children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp2 / 7][mp2 % 7]][mp2 / 7][mp2 % 7]++;
                                                    children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp1 / 7][mp1 % 7]][mp2 / 7][mp2 % 7]--;
                                                    children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp2 / 7][mp2 % 7]][mp1 / 7][mp1 % 7]--;
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }else {
                                for (int classIndex1 = (DAY_CNT * PERIOD_CNT) - 1; classIndex1 > -1; classIndex1--) {
                                    if (children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp1 / 7][mp1 % 7]][classIndex1 / 7][classIndex1 % 7] == 1) {
                                        mp2 = classIndex1;
                                        //HC6,8,12,13,14: mp2 不可以是班、周會或聯課 & mp1!=mp2  & 不用換相同科目的
                                        if (mp2 != 0 && mp2 != 9 && mp2 != 10 && mp1 != mp2 && m_classSubjectTable[mpop][mp1] != m_classSubjectTable[mpop][mp2]) {
                                            if (children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp2 / 7][mp2 % 7]][mp1 / 7][mp1 % 7] == 1) {
                                                out = 1;
                                                m_classSubjectTable[mpop][mp1] = m_classSubjectTable[mpop][mp2];
                                                m_classSubjectTable[mpop][mp2] = temp_classSubjectTable[mpop][mp1];
                                                m_classTeacherTable[mpop][mp1] = temp_classTeacherTable[mpop][mp2];
                                                m_classTeacherTable[mpop][mp2] = temp_classTeacherTable[mpop][mp1];

                                                children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp1 / 7][mp1 % 7]][mp1 / 7][mp1 % 7]++;
                                                children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp2 / 7][mp2 % 7]][mp2 / 7][mp2 % 7]++;
                                                children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp1 / 7][mp1 % 7]][mp2 / 7][mp2 % 7]--;
                                                children_teacherActualTimetable[mpop][children_classTeacherTable[mpop][selectedMClassId][mp2 / 7][mp2 % 7]][mp1 / 7][mp1 % 7]--;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }while(out == 0);

                        int countmm = 0;
                        for (int children_d = 0; children_d < (DAY_CNT+1); children_d++)
                        {
                            for (int children_p = 0; children_p < (PERIOD_CNT+1); children_p++)
                            {
                                children_classSubjectTable[mpop][selectedMClassId][children_d][children_p] = m_classSubjectTable[mpop][countmm];
                                children_classTeacherTable[mpop][selectedMClassId][children_d][children_p] = m_classTeacherTable[mpop][countmm];
                                countmm++;
                            }
                        }
                    }
                }
                pidx++;
            }

//            System.out.println("Running replacement...");

            //TODO 取代: children 成為下一個 generation 的父代
            teacherActualTimetable = Arrays.copyOf(children_teacherActualTimetable, children_teacherActualTimetable.length);
            classSubjectCnt = Arrays.copyOf(children_classSubjectCnt, children_classSubjectCnt.length);
            subjectRoomCnt = Arrays.copyOf(children_subjectRoomCnt, children_subjectRoomCnt.length);
            classSubjectTable = Arrays.copyOf(children_classSubjectTable, children_classSubjectTable.length);
            classTeacherTable = Arrays.copyOf(children_classTeacherTable, children_classTeacherTable.length);
            classAssignedTeacher = Arrays.copyOf(children_classAssignedTeacher, children_classAssignedTeacher.length);

//            System.out.println("Running calculation for fitness...");

            // 計算子代適應度
            objectVal = new double[POPULATION_SIZE + 1];
            fitness = new double[POPULATION_SIZE + 1];
            // SC1: 減少教師的空堂時間 (15)
            J = new double[POPULATION_SIZE + 1][TEACHER_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
            fitnessJ = new double[POPULATION_SIZE + 1];
            for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
                for (int tid = 1; tid < (TEACHER_CNT + 1); tid++) {
                    for (int day = 1; day < (DAY_CNT + 1); day++) {
                        for (int period = 2; period < 7; period++) {
                            J[popIndex][tid][day][period] = (double) teacherActualTimetable[popIndex][tid][day][period - 1]
                                    - (double) teacherActualTimetable[popIndex][tid][day][period]
                                    + (double) teacherActualTimetable[popIndex][tid][day][period + 1] - 1;
                            // SC1: 減少教師的空堂時間 (16)
                            if (J[popIndex][tid][day][period] < 0) {
                                J[popIndex][tid][day][period] = 0;
                            }
                            fitnessJ[popIndex] += J[popIndex][tid][day][period];
                        }
                    }
                }
            }

            // SC2: 降低教師每周的工作日 (17)
            Y = new double[POPULATION_SIZE + 1][TEACHER_CNT + 1][DAY_CNT + 1];
            D = new double[POPULATION_SIZE + 1][TEACHER_CNT + 1];
            fitnessY = new double[POPULATION_SIZE + 1];
            for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
                for (int tid = 1; tid < (TEACHER_CNT + 1); tid++) {
                    for (int day = 1; day < (DAY_CNT + 1); day++) {
                        for (int period = 1; period < (PERIOD_CNT + 1); period++) {
                            if (teacherActualTimetable[popIndex][tid][day][period] != 0) {
                                Y[popIndex][tid][day] = 1;
                                break;
                            }
                        }
                        D[popIndex][tid] += Y[popIndex][tid][day];
                    }
                    fitnessY[popIndex] += D[popIndex][tid];
                }
            }

            // 整理老師每周最低上課時數的數據，可用 csv 檔匯入
            // 記錄老師每周最低上課時數的數據 (minTeachingTime.csv)
            O = new int[TEACHER_CNT+ 1];
            try (var fr = new FileReader("src/main/resources/minTeachingTime.csv", StandardCharsets.UTF_8);
                 var reader = new CSVReader(fr)) {
                int stringIndex = 0;
                String[] nextLine;
                while ((nextLine = reader.readNext()) != null) {
                    for (int i = 1; i < O.length; i++) {
                        O[i] = Integer.parseInt(nextLine[stringIndex]);
                        stringIndex++;
                    }
                }
            }
            // SC3: 平衡教師不需要工作的天數 (19)
            MD = new double[POPULATION_SIZE+1][TEACHER_CNT+1];
            for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
                for (int tid = 1; tid < (TEACHER_CNT + 1); tid++) {
                    MD[popIndex][tid] = O[tid] / (PERIOD_CNT+1);
                }
            }

            // SC3: 平衡教師不需要工作的天數 (20)
            BETA = new double[POPULATION_SIZE+1];
            extra = new double[POPULATION_SIZE+1][TEACHER_CNT+1];
            for(int popIndex = 0; popIndex<((POPULATION_SIZE+1));popIndex++){
                for (int tid = 1; tid < (TEACHER_CNT + 1); tid++) {
                    extra[popIndex][tid] = D[popIndex][tid] = MD[popIndex][tid];
                    if(extra[popIndex][tid]>BETA[popIndex]){
                        BETA[popIndex] = extra[popIndex][tid];
                    }

                }
            }

            // SC4: 降低教師每周的實際工作節次數 (22)
            M = new double[POPULATION_SIZE+1][TEACHER_CNT+1];   //老師t一周實際工作節次數量
            for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
                for (int tid = 1; tid < (TEACHER_CNT + 1); tid++) {
                    for( int day = 1; day<(DAY_CNT+1);day++) {
                        for (int period = 1; period < (PERIOD_CNT + 1); period++) {
                            M[popIndex][tid] += teacherActualTimetable[popIndex][tid][day][period];
                        }
                    }
                }
            }
            // SC4: 降低教師每周的實際工作節次數 (23)
            teacherExtraPeriods = new double[POPULATION_SIZE+1][TEACHER_CNT+1];
            BETA1 = new double[POPULATION_SIZE+1];
            for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
                for (int tid = 1; tid < (TEACHER_CNT + 1); tid++) {

                    teacherExtraPeriods[popIndex][tid] = M[popIndex][tid] - O[tid];
                    if(teacherExtraPeriods[popIndex][tid]>BETA1[popIndex])
                        BETA1[popIndex] = teacherExtraPeriods[popIndex][tid];
                }
            }
            // SC5: 降低教師於上午四節的連續排課數量 - owner: chloe
            U = new double[POPULATION_SIZE + 1];
            teacherCalsPeriods = new double[POPULATION_SIZE + 1][TEACHER_CNT + 1][DAY_CNT + 1];
            for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
                for (int tid = 1; tid < (TEACHER_CNT + 1); tid++) {
                    for (int day = 1; day < (DAY_CNT + 1); day++) {

                        //降低上午四節的連續排課數量
                        for (int period = 1; period < 5 ; period++) {
                            teacherCalsPeriods[popIndex][tid][day]+= teacherActualTimetable[popIndex][tid][day][period];
                        }

                        if (teacherCalsPeriods[popIndex][tid][day] > U[popIndex])
                            U[popIndex] = teacherCalsPeriods[popIndex][tid][day];
                    }

                }
            }
            // SC6: 降低教師下午三節的連續排課數量 - owner: chloe
            V = new double[POPULATION_SIZE+1];
            teacherCalsPeriods2 = new double[POPULATION_SIZE + 1][TEACHER_CNT + 1][DAY_CNT + 1];
            for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
                for (int tid = 1; tid < (TEACHER_CNT + 1); tid++) {
                    for (int day = 1; day < (DAY_CNT + 1); day++) {

                        //降低下午三節的連續排課數量
                        for (int period = 5; period <(PERIOD_CNT+1) ; period++) {
                            teacherCalsPeriods2[popIndex][tid][day]+= teacherActualTimetable[popIndex][tid][day][period];
                        }

                        if (teacherCalsPeriods2[popIndex][tid][day] > V[popIndex])
                            V[popIndex] = teacherCalsPeriods2[popIndex][tid][day];
                    }
                }
            }

            // SC7: 降低教師午休時間前後的連續排課數量 - owner: chloe
            W = new double[POPULATION_SIZE+1];
            teacherCalsPeriods3 = new double[POPULATION_SIZE + 1][TEACHER_CNT + 1][DAY_CNT + 1];
            for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
                for (int tid = 1; tid < (TEACHER_CNT + 1); tid++) {
                    for (int day = 1; day < (DAY_CNT + 1); day++) {

                        //降低午休前後的連續排課數量
                        for (int period = 4; period <5 ; period++) {
                            teacherCalsPeriods3[popIndex][tid][day]+= teacherActualTimetable[popIndex][tid][day][period];
                        }

                        if (teacherCalsPeriods3[popIndex][tid][day] > W[popIndex])
                            W[popIndex] = teacherCalsPeriods3[popIndex][tid][day];
                    }

                }
            }

            // 加總上面的分數
            totalFitness = 0.0;

            for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
                objectVal[popIndex] = alpha1 * fitnessJ[popIndex] + alpha2 * fitnessY[popIndex]+alpha3*BETA[popIndex]+alpha4*BETA1[popIndex]+alpha5*U[popIndex]+alpha6*V[popIndex]+alpha7*W[popIndex];
                // 適應值為目標值的倒數
                fitness[popIndex] = 1.0 / objectVal[popIndex];
                totalFitness += fitness[popIndex];
            }

            // 輸出子代最佳的適應值
            double children_bestFitnessVal = 0.0;
            int children_bestPopIndex = 0;
            for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
                if (fitness[popIndex] > children_bestFitnessVal) {
                    children_bestFitnessVal = fitness[popIndex];
                    children_bestPopIndex = popIndex;
                }
            }
            double children_bestObjVal = 1 / children_bestFitnessVal;

            System.out.println("\tGeneration " + generation + " Best Fitness: " + children_bestFitnessVal + " (ObjVal: " + children_bestObjVal + ")");
            if (DEBUG) {
                if (generation == (MAX_GENERATIONS - 1)) {
                    for (int i = 0; i < fitness.length; i++) {
                        System.out.println("\t\tfitness[" + i + "]: " + fitness[i] + " (ObjVal: " + 1 / fitness[i] + ")");
                    }
                }
            }

            generation++;
        }

        // 記錄最佳的適應值 & 目標值
        double bestFitnessVal = 0.0;
        int bestPopIndex = 0;
        for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
            if (fitness[popIndex] > bestFitnessVal) {
                bestFitnessVal = fitness[popIndex];
                bestPopIndex = popIndex;
            }
        }
        double bestObjVal = 1 / bestFitnessVal;
        // 印出適應度最佳的班級課表
        System.out.println();
        System.out.println("Best Fitness Value: " + bestFitnessVal + " (ObjVal: " + bestObjVal + ")");
        System.out.println("░░░░░ Output The Best Population ░░░░░");
        // 記錄科目的中文名稱 (subjectChineseName.csv)
        String[] subjectChineseName = new String[SUBJECT_CNT + 1];
        try (var fr = new FileReader("src/main/resources/subjectChineseName.csv", StandardCharsets.UTF_8);
             var reader = new CSVReader(fr)) {
            int stringIndex = 0;
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                for (int i = 1; i < subjectChineseName.length; i++) {
                    subjectChineseName[i] = nextLine[stringIndex];
                    stringIndex++;
                }
            }
        }
        //開始連結　MySQL DATABASE
        int batchSize = 100;
        Connection connection = null;
        connection = DriverManager.getConnection(jdbcURL, username, password);
                 /*begin the transaction
                    -- we have to disable the auto commit mode to enable two or more statements to be grouped into a transaction)*/
        connection.setAutoCommit(false);
        String sql = "INSERT INTO output_classtimetable (idClassOutput,moduleOutput_teacherOutput) VALUES (?,?)";
        PreparedStatement statement = connection.prepareStatement(sql);

        // DEBUG 模式下才印出 5 * 7 的課表
        if (DEBUG) {
            // 跳過 classIdx == 0
            for (int classIdx = 1; classIdx < CLASS_CNT + 1; classIdx++) {
//            if (classIdx == 1) {
                System.out.println("Population " + bestPopIndex + " / Class " + classIdx + " :");


                // 橫著輸出課表
                for (int period = 1; period < PERIOD_CNT + 1; period++) {
                    for (int day = 1; day < DAY_CNT + 1; day++) {
                        // 科目 : 授課老師
                        int subjectIndex = classSubjectTable[bestPopIndex][classIdx][day][period];
                        String name = subjectChineseName[subjectIndex];
                        if (name == null) {
                            System.out.print("null + ");
                            System.out.print(classSubjectTable[bestPopIndex][classIdx][day][period] + ":");
                        }
                        else {
                            System.out.print(name + " : ");
                        }
                        int tidx = classAssignedTeacher[bestPopIndex][classIdx][subjectIndex];
                        System.out.print(tidx + "\t\t\t\t\t\t");

                        if (day == DAY_CNT) {
                            System.out.println();
                        }
                    }
                    if (period == PERIOD_CNT) {
                        System.out.println();
                    }
                }


//            }
            }

        }

        // 非 DEBUG 模式將課表印成字串
        else {
            // 跳過 classIdx == 0
            for (int classIdx = 1; classIdx < CLASS_CNT + 1; classIdx++) {
                System.out.println("Population " + bestPopIndex + " / Class " + classIdx + " :");
                statement.setString(1, String.valueOf(classIdx));

                ArrayList<String> module_teacher = new ArrayList();
                // 橫著輸出課表
                for (int day = 1; day < DAY_CNT + 1; day++) {
                    for (int period = 1; period < PERIOD_CNT + 1; period++) {
                        // 科目 : 授課老師
                        int subjectIndex = classSubjectTable[bestPopIndex][classIdx][day][period];
                        String name = subjectChineseName[subjectIndex];
                        if (name == null) {
                            System.out.print("null + ");
                            System.out.print(classSubjectTable[bestPopIndex][classIdx][day][period] + ":");
                        }
                        else {
                            System.out.print(name + ":");
                        }
                        int tidx = classAssignedTeacher[bestPopIndex][classIdx][subjectIndex];
                        statement.setString(2, name);
                        module_teacher.add(name+":"+tidx);
                        System.out.print(tidx);
                        if (!(day == DAY_CNT && period == PERIOD_CNT)) {
                            System.out.print(",");
                        }
                    }
                    if (day == DAY_CNT) {
                        System.out.println();
                    }

                }
                String allData = module_teacher.toString();
                statement.setString(2,allData);
                statement.addBatch();
                // execute the remaining queries
                statement.executeBatch();

                //commit the transaction
                connection.commit();

                //        connection.rollback();

                System.out.println("Finished write output_classtimetable in MySQL! ");
            }
            connection.close();
        }

        int batchSize2 = 50;
        Connection connection2 = null;
        connection2 = DriverManager.getConnection(jdbcURL, username, password);
                 /*begin the transaction
                    -- we have to disable the auto commit mode to enable two or more statements to be grouped into a transaction)*/
        connection2.setAutoCommit(false);
        String sql2 = "INSERT INTO output_teachertimetable (idTeacherOutput,classIDs) VALUES (?,?)";
        PreparedStatement statement2 = connection2.prepareStatement(sql2);
        // 印出適應度最佳的教師課表
        int[][][] teacherSubjecTable = new int[TEACHER_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
        int[][][] teacherClassTable = new int[TEACHER_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
        // 跳過 classIdx == 0
        for (int classIdx = 1; classIdx < CLASS_CNT + 1; classIdx++) {
            for (int day = 1; day < DAY_CNT + 1; day++) {
                for (int period = 1; period < PERIOD_CNT + 1; period++) {
                    // 科目 : 授課老師
                    int subjectIndex = classSubjectTable[bestPopIndex][classIdx][day][period];
                    int tidx = classAssignedTeacher[bestPopIndex][classIdx][subjectIndex];
                    teacherSubjecTable[tidx][day][period] = subjectIndex;
                    teacherClassTable[tidx][day][period] = classIdx;
                }
            }
        }

        // DEBUG 模式下才印出 5 * 7 的課表
        if (DEBUG) {
            // 跳過 teacherIdx == 0
            for (int teacherIdx = 1; teacherIdx < TEACHER_CNT + 1; teacherIdx++) {
                System.out.println("Population " + bestPopIndex + " / Teacher " + teacherIdx + " :");

                // 橫著輸出課表
                for (int period = 1; period < PERIOD_CNT + 1; period++) {
                    for (int day = 1; day < DAY_CNT + 1; day++) {
                        // 科目 : 授課老師
                        int subjectIndex = teacherSubjecTable[teacherIdx][day][period];
                        String name = subjectChineseName[subjectIndex];
                        if (name == null) {
                            System.out.print("BLANK");

                        }
                        else {
                            System.out.print(name + " : ");
                        }
                        int cidx = teacherClassTable[teacherIdx][day][period];
                        if (cidx == 0) {
                            // pass
                        }
                        else {
                            System.out.print(cidx);

                        }
                        System.out.print("\t\t\t\t\t\t");
                        if (day == DAY_CNT) {
                            System.out.println();
                        }
                    }
                    if (period == PERIOD_CNT) {
                        System.out.println();
                    }
                }

            }
            connection2.close();
        }
        // 非 DEBUG 模式將課表印成字串
        else {

            // 跳過 teacherIdx == 0
            for (int teacherIdx = 1; teacherIdx < TEACHER_CNT + 1; teacherIdx++) {
                System.out.println("Population " + bestPopIndex + " / Teacher " + teacherIdx + " :");
                statement2.setString(1, String.valueOf(teacherIdx));
                ArrayList<String> classIDs = new ArrayList();
                // 橫著輸出課表
                for (int day = 1; day < DAY_CNT + 1; day++) {
                    for (int period = 1; period < PERIOD_CNT + 1; period++) {
                        // 科目 : 授課老師
                        int subjectIndex = teacherSubjecTable[teacherIdx][day][period];
                        String name = subjectChineseName[subjectIndex];
                        if (name == null) {
                            System.out.print("BLANK");
                            classIDs.add("N");
                        }
                        else {
                            System.out.print(name + ":");
                        }
                        int cidx = teacherClassTable[teacherIdx][day][period];
                        if (cidx == 0) {
                            // pass
                        }
                        else {
                            System.out.print(cidx);
                            classIDs.add(name + " : "+cidx);
                        }
                        if (!(day == DAY_CNT && period == PERIOD_CNT)) {
                            System.out.print(",");
                        }
                    }
                    if (day == DAY_CNT) {
                        System.out.println();
                    }
                }
                String allData2 = classIDs.toString();
                statement2.setString(2,allData2);

                statement2.addBatch();
                // execute the remaining queries
                statement2.executeBatch();

                //commit the transaction
                connection2.commit();
            }
            connection2.close();
        }

        // 記錄程式執行完畢的時間
        long endTime = System.nanoTime();
        // 計算執行程式的 elapsed time (in nanoseconds)
        long timeElapsed = endTime - startTime;
        System.out.println("Execution time: " + timeElapsed / 1000000 + " milliseconds ("
                + timeElapsed / 1000000000 / 60 + " mins " + timeElapsed / 1000000000 % 60 + " secs)");
    }
}
