import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
    private static final int POPULATION_SIZE = 1;
    private static final int MAX_GENERATIONS = 10;


    // IMPORTANT NOTE: 程式陣列長度皆為實際需要長度再加 1
    public static void main(String[] args) throws IOException,
            CsvValidationException {

        System.out.println("Running...");
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
        // TODO: 記錄老師偏好的授課的時間
        // int[][][][] teacherPreferredTimetable = new int[POPULATION_SIZE + 1][TEACHER_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
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

                            // 略過班會、周會、體育課
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

//        // 輸出班級課表
//        // 記錄科目的中文名稱 (subjectChineseName.csv)
//        String[] subjectChineseName = new String[SUBJECT_CNT + 1];
//        try (var fr = new FileReader("src/main/resources/subjectChineseName.csv", StandardCharsets.UTF_8);
//             var reader = new CSVReader(fr)) {
//            int stringIndex = 0;
//            String[] nextLine;
//            while ((nextLine = reader.readNext()) != null) {
//                for (int i = 1; i < subjectChineseName.length; i++) {
//                    subjectChineseName[i] = nextLine[stringIndex];
//                    stringIndex++;
//                }
//            }
//        }
//
//        for (int popIdx = 0; popIdx < (POPULATION_SIZE + 1); popIdx++) {
//            System.out.println();
//            System.out.println("░░░░░ Output Population " + popIdx + "'s data ░░░░░");
//            // 跳過 classIdx == 0
//            for (int classIdx = 1; classIdx < CLASS_CNT + 1; classIdx++) {
//
//                System.out.println("Population " + popIdx + " / Class " + classIdx + " :");
//                // 橫著輸出課表
//                for (int period = 1; period < PERIOD_CNT + 1; period++) {
//                    for (int day = 1; day < DAY_CNT + 1; day++) {
//                        // 科目 : 授課老師
//                        int subjectIndex = classSubjectTable[popIdx][classIdx][day][period];
//                        String name = subjectChineseName[subjectIndex];
//                        if (name == null) {
//                            System.out.print("null + ");
//                            System.out.print(classSubjectTable[popIdx][classIdx][day][period] + ":");
//                        }
//                        else {
//                            System.out.print(name + " : ");
//                        }
//                        System.out.print(classTeacherTable[popIdx][classIdx][day][period] + "\t\t\t\t\t\t");
//                        if (day == DAY_CNT) {
//                            System.out.println();
//                        }
//                    }
//                    if (period == PERIOD_CNT) {
//                        System.out.println();
//                    }
//                }
//            }
//        }

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

        // TODO: 整理老師每周最低上課時數的數據，可用 csv 檔匯入
        // TODO: SC3: 平衡教師不需要工作的天數 (19)
        // TODO: SC3: 平衡教師不需要工作的天數 (20)

        // TODO: SC4: 降低教師每周的實際工作節次數 (22)
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


        // TODO: SC4: 降低教師每周的實際工作節次數 (23)
        //PARAMETER : O[]
        int O [] = new int[143]; //TODO: 應放在SC3

        double teacherExtraPeriods [][] = new double[POPULATION_SIZE+1][TEACHER_CNT+1];
        double BETA1[] = new double[POPULATION_SIZE+1];
        for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
            for (int tid = 1; tid < (TEACHER_CNT + 1); tid++) {

                teacherExtraPeriods[popIndex][tid] = M[popIndex][tid] - O[tid];
                if(teacherExtraPeriods[popIndex][tid]>BETA1[popIndex])
                    BETA1[popIndex] = teacherExtraPeriods[popIndex][tid];
            }
        }

        // TODO: SC5: 降低教師於上午四節的連續排課數量 - owner: chloe
        double U []= new double[POPULATION_SIZE + 1];
        double teacherCalsPeriods[][][]= new double[POPULATION_SIZE + 1][TEACHER_CNT + 1][DAY_CNT + 1];
        for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
            for (int tid = 1; tid < (TEACHER_CNT + 1); tid++) {
                for (int day = 1; day < (DAY_CNT + 1); day++) {

                    //降低上午四節的連續排課數量
                    for (int period = 1; period < 5 ; period++) {  //ATTENTION :5
                        teacherCalsPeriods[popIndex][tid][day]+= teacherActualTimetable[popIndex][tid][day][period];
                    }

                    if (teacherCalsPeriods[popIndex][tid][day] > U[popIndex])
                        U[popIndex] = teacherCalsPeriods[popIndex][tid][day];
                }

            }
        }

        // TODO: SC6: 降低教師下午三節的連續排課數量 - owner: chloe
        double V[] = new double[POPULATION_SIZE+1];
        double teacherCalsPeriods2[][][] = new double[POPULATION_SIZE + 1][TEACHER_CNT + 1][DAY_CNT + 1];
        for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
            for (int tid = 1; tid < (TEACHER_CNT + 1); tid++) {
                for (int day = 1; day < (DAY_CNT + 1); day++) {

                    //降低下午三節的連續排課數量
                    for (int period = 5; period <(PERIOD_CNT+1) ; period++) {  //ATTENTION :5
                        teacherCalsPeriods2[popIndex][tid][day]+= teacherActualTimetable[popIndex][tid][day][period];
                    }

                    if (teacherCalsPeriods2[popIndex][tid][day] > V[popIndex])
                        V[popIndex] = teacherCalsPeriods2[popIndex][tid][day];
                }

            }
        }

        // TODO: SC7: 降低教師午休時間前後的連續排課數量 - owner: chloe
        double  W[] = new double[POPULATION_SIZE+1];
        double teacherCalsPeriods3[][][] = new double[POPULATION_SIZE + 1][TEACHER_CNT + 1][DAY_CNT + 1];
        for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
            for (int tid = 1; tid < (TEACHER_CNT + 1); tid++) {
                for (int day = 1; day < (DAY_CNT + 1); day++) {

                    //降低午休前後的連續排課數量
                    for (int period = 4; period <5 ; period++) {  //ATTENTION :4,5
                        teacherCalsPeriods3[popIndex][tid][day]+= teacherActualTimetable[popIndex][tid][day][period];
                    }

                    if (teacherCalsPeriods3[popIndex][tid][day] > W[popIndex])
                        W[popIndex] = teacherCalsPeriods3[popIndex][tid][day];
                }

            }
        }

        // 加總上面的分數
        double totalFitness = 0.0;
        // 各參數比重先寫死，之後可再優化
        // TODO: 還需要 alpha3 - alpha7
        double alpha1 = 1;
        double alpha2 = 1;
        for (int popIndex = 0; popIndex < (POPULATION_SIZE + 1); popIndex++) {
            // TODO: 等 SC3 - SC4 完成後要修改此處
            objectVal[popIndex] = alpha1 * fitnessJ[popIndex] + alpha2 * fitnessY[popIndex];
            // 適應值為目標值的倒數
            fitness[popIndex] = 1.0 / objectVal[popIndex];
            totalFitness += fitness[popIndex];
        }

        // 開始執行 GA 疊代
        int generation = 0;
        while (generation < MAX_GENERATIONS) {
            // do something
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

        // 記錄程式執行完畢的時間
        long endTime = System.nanoTime();
        // 計算執行程式的 elapsed time (in nanoseconds)
        long timeElapsed = endTime - startTime;
        System.out.println("Execution time: " + timeElapsed / 1000000 + " milliseconds");
    }
}
