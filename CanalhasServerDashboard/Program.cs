namespace CanalhasServerDashboard;

static class Program
{
    /// <summary>
    ///  The main entry point for the application.
    /// </summary>
    [STAThread]
    static void Main()
    {
        try
        {
            ApplicationConfiguration.Initialize();
            Application.Run(new DashboardForm());
        }
        catch (Exception ex)
        {
            File.WriteAllText(Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "dashboard_crash.log"), ex.ToString());
            MessageBox.Show(ex.ToString(), "Erro ao iniciar CanalhasServerDashboard", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }    
}