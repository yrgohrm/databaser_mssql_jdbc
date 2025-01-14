# A simple Microsoft SQL Server JDBC example

This is a super basic example to get started with MS SQL Server and JDBC.

## Running

First of all you need a database. This simple example has been tested
with MySQL 8 and get one up and running quickly with Docker.

```SH
docker run --name mssql -e "ACCEPT_EULA=Y" -e "SA_PASSWORD=secretP4ssword" \
-e "MSSQL_PID=Express" -p 1433:1433 -d mcr.microsoft.com/mssql/server:2019-latest
```

Then you need to create a database called `Warehouse` on the server.

```SQL
CREATE DATABASE Warehouse;
```

Finally you need to build the application and run it. To do this you need to
set up three environment variables. `DB_USERNAME`, `DB_PASSWORD` and `DB_HOST`.

The following will probably do it for cmd:
```
set DB_USERNAME=sa
set DB_PASSWORD=secretP4ssword
set DB_HOST=localhost
```

The equivalent for PowerShell:
```PowerShell
$env:DB_USERNAME="sa"
$env:DB_PASSWORD="secretP4ssword"
$env:DB_HOST="localhost"
```

The equivalent for bash:
```SH
export DB_USERNAME="sa"
export DB_PASSWORD="secretP4ssword"
export DB_HOST="localhost"
```

Now run (in the same shell that you set up the environment variables in):
```
mvn package
mvn flyway:migrate
mvn exec:exec
```
