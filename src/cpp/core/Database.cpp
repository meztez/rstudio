/*
 * Database.cpp
 *
 * Copyright (C) 2020 by RStudio, PBC
 *
 * Unless you have received this program directly from RStudio pursuant
 * to the terms of a commercial license agreement with RStudio, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */

#include <core/Database.hpp>

#include <boost/format.hpp>

#include <shared_core/Error.hpp>
#include <shared_core/SafeConvert.hpp>

#include <soci/postgresql/soci-postgresql.h>
#include <soci/sqlite3/soci-sqlite3.h>

// Database Boost Errors
// Declare soci errors as boost errors.
// =================================================================================================================
namespace RSTUDIO_BOOST_NAMESPACE {
namespace system {

template <>
struct is_error_code_enum<soci::soci_error::error_category>
{
   static const bool value = true;
};

} // namespace system
} // namespace boost

namespace rstudio {
namespace core {
namespace database {
   const boost::system::error_category& databaseErrorCategory();
}
}
}

namespace soci {

inline boost::system::error_code make_error_code(soci::soci_error::error_category e)
{
   return { e, rstudio::core::database::databaseErrorCategory() };
}

inline boost::system::error_condition make_error_condition(soci::soci_error::error_category e)
{
   return { e, rstudio::core::database::databaseErrorCategory() };
}

}

namespace rstudio {
namespace core {
namespace database {

class DatabaseErrorCategory : public boost::system::error_category
{
public:
   const char* name() const BOOST_NOEXCEPT override;

   std::string message(int ev) const override;
};

const boost::system::error_category& databaseErrorCategory()
{
   static DatabaseErrorCategory databaseErrorCategoryConst;
   return databaseErrorCategoryConst;
}

const char* DatabaseErrorCategory::name() const BOOST_NOEXCEPT
{
   return "database";
}

std::string DatabaseErrorCategory::message(int ev) const
{
   switch (ev)
   {
      case soci::soci_error::error_category::connection_error:
         return "Connection Error";
      case soci::soci_error::error_category::invalid_statement:
         return "Invalid Statement";
      case soci::soci_error::error_category::no_privilege:
         return "No Privilege";
      case soci::soci_error::error_category::no_data:
         return "No Data";
      case soci::soci_error::error_category::constraint_violation:
         return "Constraint Violation";
      case soci::soci_error::error_category::unknown_transaction_state:
         return "Unknown Transaction State";
      case soci::soci_error::error_category::system_error:
         return "System Error";
      case soci::soci_error::error_category::unknown:
      default:
         return "Unknown Error";
   }
}

#define DatabaseError(sociError) Error(sociError.get_error_category(), sociError.get_error_message(), ERROR_LOCATION);

// Database errors =================================================================================================

class ConnectVisitor : public boost::static_visitor<Error>
{
public:
   ConnectVisitor(boost::shared_ptr<Connection>* pPtrConnection) :
      pPtrConnection_(pPtrConnection)
   {
   }

   Error operator()(const SqliteConnectionOptions& options) const
   {
      try
      {
         boost::shared_ptr<Connection> pConnection(new Connection(soci::sqlite3, "dbname=\"" + options.file + "\""));
         *pPtrConnection_ = pConnection;
         return Success();
      }
      catch (soci::soci_error& error)
      {
         return DatabaseError(error);
      }
   }

   Error operator()(const PostgresqlConnectionOptions& options) const
   {
      try
      {
         boost::format fmt("host='%1%' port='%2%' dbname='%3%' user='%4%' password='%5%' connect_timeout='%6%'");
         std::string connectionStr =
               boost::str(fmt %
                          options.host %
                          options.port %
                          options.database %
                          options.user %
                          options.password %
                          safe_convert::numberToString(options.connectionTimeoutSeconds, "0"));

         boost::shared_ptr<Connection> pConnection(new Connection(soci::postgresql, connectionStr));
         *pPtrConnection_ = pConnection;
         return Success();
      }
      catch (soci::soci_error& error)
      {
         return DatabaseError(error);
      }
   }

private:
   boost::shared_ptr<Connection>* pPtrConnection_;
};

Query::Query(const std::string& sqlStatement,
             soci::session& session) :
   statement_(session)
{
   // it's possible that prepare can throw a database exception, but we
   // do not want to surface errors until execute() is called
   try
   {
      statement_.prepare(sqlStatement);
   }
   catch (soci::soci_error& error)
   {
      prepareError_ = error;
   }
}

Connection::Connection(const soci::backend_factory& factory,
                       const std::string& connectionStr) :
   session_(factory, connectionStr)
{
}

Query Connection::query(const std::string& sqlStatement)
{
   return Query(sqlStatement, session_);
}

Error Connection::execute(Query& query,
                          bool* pDataReturned)
{
   if (query.prepareError_)
      return DatabaseError(query.prepareError_.get());

   try
   {
      query.statement_.define_and_bind();
      bool result = query.statement_.execute(true);

      if (pDataReturned)
         *pDataReturned = result;

      query.statement_.bind_clean_up();
   }
   catch (soci::soci_error& error)
   {
      return DatabaseError(error);
   }

   return Success();
}

PooledConnection::PooledConnection(const boost::shared_ptr<ConnectionPool>& pool,
                                   const boost::shared_ptr<Connection>& connection) :
   pool_(pool),
   connection_(connection)
{
}

PooledConnection::~PooledConnection()
{
   pool_->returnConnection(connection_);
}

Query PooledConnection::query(const std::string& sqlStatement)
{
   return connection_->query(sqlStatement);
}

Error PooledConnection::execute(Query& query,
                                bool* pDataReturned)
{
   return connection_->execute(query, pDataReturned);
}

boost::shared_ptr<PooledConnection> ConnectionPool::getConnection()
{
   // block until a connection is available
   boost::shared_ptr<Connection> connection;
   connections_.deque(&connection, boost::posix_time::pos_infin);

   // create wrapper PooledConnection around retrieved Connection
   return boost::shared_ptr<PooledConnection>(new PooledConnection(shared_from_this(), connection));
}

void ConnectionPool::returnConnection(const boost::shared_ptr<Connection>& connection)
{
   connections_.enque(connection);
}

Transaction::Transaction(const boost::shared_ptr<Connection>& connection) :
   connection_(connection),
   transaction_(connection->session_)
{
}

void Transaction::commit()
{
   transaction_.commit();
}

void Transaction::rollback()
{
   transaction_.rollback();
}

Error connect(const ConnectionOptions& options,
              boost::shared_ptr<Connection>* pPtrConnection)
{
   return boost::apply_visitor(ConnectVisitor(pPtrConnection), options);
}

Error createConnectionPool(size_t poolSize,
                           const ConnectionOptions& options,
                           boost::shared_ptr<ConnectionPool>* pPool)
{
   pPool->reset(new ConnectionPool());

   for (size_t i = 0; i < poolSize; ++i)
   {
      boost::shared_ptr<Connection> connection;
      Error error = connect(options, &connection);
      if (error)
      {
         // destroy the pool, which will free each previously created connections
         pPool->reset();
         return error;
      }

      // add connection to the pool
      (*pPool)->returnConnection(connection);
   }

   return Success();
}

} // namespace database
} // namespace core
} // namespace rstudio