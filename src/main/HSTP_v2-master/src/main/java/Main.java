import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Main {
    static String jdbcURL = "jdbc:mysql://localhost:3306/hstp?sessionVariables=sql_mode='NO_ENGINE_SUBSTITUTION'&jdbcCompliantTruncation=false";
    static String username = "root";
    static String password = "m0920776286";

    public static void main(String[] args) throws SQLException {

        writeClassTimeTableToMySQL();
//        writeTeacherTimeTableToMySQL();
    }

    //TODO 1 : 設定此檔案Mysql連線
    //TODO 2 : 寫 Connection() 連結

    public static void writeClassTimeTableToMySQL() throws SQLException {
        int batchSize = 50;
        Connection connection = null;
        connection = DriverManager.getConnection(jdbcURL, username, password);
         /*begin the transaction
            -- we have to disable the auto commit mode to enable two or more statements to be grouped into a transaction)*/
        connection.setAutoCommit(false);

        String sql = "INSERT INTO output_classtimetable (idClassOutput,moduleOutput_teacherOutput) VALUES (?,?)";
        PreparedStatement statement = connection.prepareStatement(sql);

        statement.setString(1,"701");
        statement.setString(2,"國文:21");
        statement.addBatch();
        // execute the remaining queries
        statement.executeBatch();

        //commit the transaction
        connection.commit();
        connection.close();
//        connection.rollback();

        System.out.println("Finished write output_classtimetable in MySQL! ");


    }
    public static void writeTeacherTimeTableToMySQL() throws SQLException {
        int batchSize = 50;
        Connection connection = null;
        connection = DriverManager.getConnection(jdbcURL, username, password);
         /*begin the transaction
            -- we have to disable the auto commit mode to enable two or more statements to be grouped into a transaction)*/
        connection.setAutoCommit(false);

        String sql = "INSERT INTO output_teachertimetable (idTeacherOutput,classIDs) VALUES (?,?)";
        PreparedStatement statement = connection.prepareStatement(sql);


    }

}
