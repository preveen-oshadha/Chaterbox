/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package model;


public class validations {
     public static boolean IsEmailValid(String email){
        return email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    }
    public static boolean IsPassworrdValid(String password){
        return password.matches("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$");
    }
    public static boolean isDouble(String text){
        return text.matches("^\\d{1,9}(\\.\\d{1,2})?$");
    }
    public static boolean isInteger(String text){
        return text.matches("^\\d+$");
    }
    public static boolean isMobileValid(String moblie){
        return moblie.matches("^(\\\\+94|0)?7[0-9]{8}$");
    }
}
