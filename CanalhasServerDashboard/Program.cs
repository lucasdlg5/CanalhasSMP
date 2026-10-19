namespace CanalhasServerDashboard;

static class Program
{
    /// <summary>
    ///  The main entry point for the application.
    /// </summary>
    [STAThread]
    static void Main()
    {
        Application.SetUnhandledExceptionMode(UnhandledExceptionMode.CatchException);
        Application.ThreadException += (s, e) =>
        {
            LogCrash(e.Exception);
            MessageBox.Show($"Ocorreu um erro no Dashboard:\n{e.Exception.Message}\n\nDetalhes gravados em dashboard_crash.log", "CanalhasServerDashboard - Erro", MessageBoxButtons.OK, MessageBoxIcon.Warning);
        };
        AppDomain.CurrentDomain.UnhandledException += (s, e) =>
        {
            if (e.ExceptionObject is Exception ex)
            {
                LogCrash(ex);
            }
        };

        try
        {
            ApplicationConfiguration.Initialize();
            Application.Run(new DashboardForm());
        }
        catch (Exception ex)
        {
            LogCrash(ex);
            MessageBox.Show(ex.ToString(), "Erro ao iniciar CanalhasServerDashboard", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    private static void LogCrash(Exception ex)
    {
        try
        {
            string path = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "dashboard_crash.log");
            File.AppendAllText(path, $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] {ex}{Environment.NewLine}{Environment.NewLine}");
        }
        catch { }
    }
}