using Microsoft.Win32;
using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

[assembly: System.Reflection.AssemblyTitle("Uninstall SyncDows")]
[assembly: System.Reflection.AssemblyProduct("SyncDows")]
[assembly: System.Reflection.AssemblyCompany("Fullm3t41")]
[assembly: System.Reflection.AssemblyVersion("1.0.0.0")]

internal static class UninstallSyncDows
{
    private const string UninstallRegistryPath = @"Software\Microsoft\Windows\CurrentVersion\Uninstall";

    [STAThread]
    private static int Main()
    {
        try
        {
            string command = FindUninstallCommand();
            if (string.IsNullOrWhiteSpace(command))
            {
                MessageBox.Show(
                    "Windows could not find the registered SyncDows uninstaller. You can also remove SyncDows from Settings > Apps > Installed apps.",
                    "Uninstall SyncDows",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Information
                );
                return 1;
            }

            string executable;
            string arguments;
            SplitCommand(command, out executable, out arguments);
            Process.Start(new ProcessStartInfo
            {
                FileName = executable,
                Arguments = arguments,
                UseShellExecute = true
            });
            return 0;
        }
        catch (Exception error)
        {
            MessageBox.Show(
                "The SyncDows uninstaller could not be started.\n\n" + error.Message,
                "Uninstall SyncDows",
                MessageBoxButtons.OK,
                MessageBoxIcon.Error
            );
            return 2;
        }
    }

    private static string FindUninstallCommand()
    {
        string msiFallback = null;
        RegistryHive[] hives = { RegistryHive.CurrentUser, RegistryHive.LocalMachine };
        RegistryView[] views = { RegistryView.Registry64, RegistryView.Registry32 };
        foreach (RegistryHive hive in hives)
        {
            foreach (RegistryView view in views)
            {
                using (RegistryKey baseKey = RegistryKey.OpenBaseKey(hive, view))
                using (RegistryKey uninstall = baseKey.OpenSubKey(UninstallRegistryPath))
                {
                    if (uninstall == null) continue;
                    foreach (string childName in uninstall.GetSubKeyNames())
                    {
                        using (RegistryKey child = uninstall.OpenSubKey(childName))
                        {
                            if (child == null) continue;
                            string displayName = child.GetValue("DisplayName") as string;
                            if (!string.Equals(displayName, "SyncDows", StringComparison.OrdinalIgnoreCase)) continue;
                            string command = child.GetValue("UninstallString") as string;
                            if (string.IsNullOrWhiteSpace(command) || command.IndexOf(".exe", StringComparison.OrdinalIgnoreCase) < 0)
                                continue;
                            if (command.IndexOf("msiexec", StringComparison.OrdinalIgnoreCase) < 0)
                                return command;
                            if (msiFallback == null) msiFallback = command;
                        }
                    }
                }
            }
        }
        return msiFallback;
    }

    private static void SplitCommand(string command, out string executable, out string arguments)
    {
        string trimmed = command.Trim();
        if (trimmed.StartsWith("\"", StringComparison.Ordinal))
        {
            int closingQuote = trimmed.IndexOf('\"', 1);
            if (closingQuote < 0) throw new InvalidDataException("The registered uninstall command is invalid.");
            executable = trimmed.Substring(1, closingQuote - 1);
            arguments = trimmed.Substring(closingQuote + 1).TrimStart();
            return;
        }
        int executableEnd = trimmed.IndexOf(".exe", StringComparison.OrdinalIgnoreCase);
        if (executableEnd < 0) throw new InvalidDataException("The registered uninstall command is invalid.");
        executableEnd += 4;
        executable = trimmed.Substring(0, executableEnd);
        arguments = trimmed.Substring(executableEnd).TrimStart();
    }
}
