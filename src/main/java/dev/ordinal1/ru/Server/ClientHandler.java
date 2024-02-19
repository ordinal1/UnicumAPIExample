package dev.ordinal1.ru.Server;

import lombok.Getter;
import lombok.Setter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.nio.charset.Charset;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.sql.SQLException;


public class ClientHandler implements Runnable {
    //Ключ шифрования настраивается в Unicum Configurator
    private static final byte[] keyBytes = new byte[]{0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8};
    //Вектор шифрования настраивается в Unicum Configurator
    private static final byte[] ivBytes = new byte[]{0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8};
    //Экземпляр сокета
    private final Socket socket;
    //Принимаем от ServerSocket экземпляр сокета
    public ClientHandler(Socket accept) {
        socket = accept;
    }

    //Метод обработки команд от вендинга
    private static byte[] getCommand(int command, String optionals) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, UnsupportedEncodingException, IllegalBlockSizeException, BadPaddingException, SQLException {
        //Инициализируем дешифратор
        Cipher cipher = Cipher.getInstance("DES/CBC/PKCS7Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "DES"), new IvParameterSpec(ivBytes));
        //Создаём байт-массив для передачи в него через case шифрованного сообщения
        byte[] encryptedResponse;
        switch (command) {
            case 1: { //Начало транзакции (ответное сообщение содержит номер транзакции checkNumber)
                byte[] response;
                int checkNumber = 1;

                response = ("1;0;" + checkNumber).getBytes("CP1251");
                encryptedResponse = cipher.doFinal(response);
                break;
            }
            case 2: { //Покупка товара, возвращаем код об успешном получении сообщения от ТА.
                byte[] response;
                response = "2;0".getBytes("CP1251");
                encryptedResponse = cipher.doFinal(response);
                break;
            }
            case 3: { //Успешное завершение продажи товара, так же отправляем сообщение об успешном получении от ТА
                //RK Transactions
                byte[] response;

                response = "3;0".getBytes("CP1251");
                encryptedResponse = cipher.doFinal(response);

                break;
            }
            case 4: { //Отмена транзакции (ошибка выдачи товара, застрял, не выпал и т.д.) так же отправляем сообщение об успешном получении от ТА
                byte[] response;
                response = "4;0".getBytes("CP1251");
                encryptedResponse = cipher.doFinal(response);
                break;
            }
            case 5: { //Запрос баланса. Здесь можем передавать наличие баланса у клиента, если используем свою ПДС, либо определенный кредит.
                // В данном случае это переменная money, которая будет равна 100 рублям (в зависимости от конфигурации может менятся)
                byte[] response;
                long money = 10000;
                //Если кредит > 0 то передаём успешно, иначе ошибка
                if (money > 0) {
                    //Успешно
                    response = ("5;0;" + money).getBytes("CP1251");
                } else {
                    //Нет средств на карте
                    response = ("5;7").getBytes("CP1251");
                }
                encryptedResponse = cipher.doFinal(response);
                break;
            }
            default: {
                //Иначе возвращаем нуль
                encryptedResponse = null;
                break;
            }
        }

        return encryptedResponse;
    }


    @Override
    public void run() {
        //Строка для передачи полного текста команды в getCommand(...). Для получения нужной информации от ТА (цена, название блюда и т.д.)
        String optional = null;
        // Создание объекта для ввода
        Security.addProvider(new BouncyCastleProvider());
        try {
            //Получаем поток сообщения от сокета
            DataInputStream inputStream = new DataInputStream(socket.getInputStream());
            //Инициализируем дешифратор
            Cipher cipher = Cipher.getInstance("DES/CBC/PKCS7Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "DES"), new IvParameterSpec(ivBytes));
            //Считываем из потока количество int символов и создаём такой же размерностью массив байтов
            byte[] encryptedData = new byte[inputStream.readInt()];
            //Читаем из потока оставшийся байт-код в ранее созданный массив
            inputStream.readFully(encryptedData);
            //Далее расшифровываем сообщение в другой байт массив через дешифратор
            byte[] decrypted = cipher.doFinal(encryptedData);
            //Получаем в виде строки принятую, расшифрованную команду
            String input = new String(decrypted, Charset.forName("CP1251"));
            //Записываем её в optional для передачи в getCommand(...).
            optional = input;

            //Выводим принятую команду в консоль
            System.out.println(input);
            //Далее создаём байтовый массив и передаём обработку в метод getCommand(...)
            byte[] encrypted = new byte[0];
            try {
                encrypted = getCommand(Integer.parseInt(input.split(";")[0]), optional);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
            //Далее создаём поток вывода (ответа от сервера)
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            //Записываем длину зашифрованного сообщения
            out.writeInt(encrypted.length);
            //Добавляем к сообщению зашифрованный массив данных
            out.write(encrypted);
            //Отправялем сообщние и закрываем.
            out.flush();
            out.close();
            //Устанавливаем тайм-аут 3000мс (3 секунды) для автозакрытия экземпляра сокета
            socket.setSoTimeout(3000);
        } catch (Exception ignored) {
        }
    }
}




