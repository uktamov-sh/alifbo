# Сервис клавиатуры и экран настроек сохраняются автоматически по манифесту.

# Страховка: даже если в код случайно попадёт вызов Log, в релизной сборке он будет вырезан.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
