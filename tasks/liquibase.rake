require 'date'
require 'erb'
require 'rexml/document'
require 'stringex_lite'

require './tasks/util'

module Liquibase
  include ::Candlepin::Util

  class << self
    def add_to_changelog(file, liquibase)
      file = File.join(liquibase.include_path, File.basename(file))
      liquibase.changelogs.each do |changelog|
        doc_file = File.join(liquibase.changelog_dir, changelog)
        doc = REXML::Document.new(File.open(doc_file))

        # Use double quotes for attributes
        doc.context[:attribute_quote] = :quote
        doc.root.add_element('include', {'file' => file})

        File.open(doc_file, 'w') do |f|
          doc.write(f, 4)
          # REXML doesn't add a newline to the end of the file.  Git complains about that.
          f.write("\n")
        end
      end
    end
  end

  class Config
    attr_writer :changelog_dir
    def changelog_dir
      @changelog_dir || File.join(project.path_to(:src, :main, :resources), include_path)
    end

    def enabled?
      File.exist?(changelog_dir)
    end

    attr_writer :changelogs
    def changelogs
      @changelogs || ['changelog.xml']
    end

    attr_writer :include_path
    def include_path
      @include_path || File.join('db', 'changelog')
    end

    attr_writer :file_time_prefix_format
    def file_time_prefix_format
      @file_time_prefix_format || '%Y-%m-%d-%H-%M'
    end

    attr_writer :template
    def template
      @template || <<-LIQUIBASE
        <?xml version="1.0" encoding="UTF-8"?>

        <databaseChangeLog
                xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-2.0.xsd">

            <changeSet id="<%= id%>" author="<%= author%>">
                <comment><%= description %></comment>
                <!-- See http://www.liquibase.org/documentation/changes/index.html -->
            </changeSet>

        </databaseChangeLog>
        <!-- vim: set expandtab sts=4 sw=4 ai: -->
      LIQUIBASE
    end

    protected
    def initialize(project)
      @project = project
    end

    attr_reader :project
  end

  class TemplateValues < Struct.new(:author, :id, :description)
    def binding
      super
    end
  end

  class ChangesetTask < Rake::Task
    attr_reader :now
    attr_reader :project

    class << self
      def run_local(description) #:nodoc:
        Project.local_projects do |local_project|
          local_project.task('changeset').invoke(description)
        end
      end
    end

    def initialize(*args)
      super
      @now = DateTime.now
    end

    def execute(args)
      super
      description = args[:description]
      liquibase = project.liquibase

      unless liquibase.enabled?
        fail("Project #{project} does not have Liquibase enabled")
      end

      values = TemplateValues.new
      values.description = description
      values.author = ENV['USER']
      values.id = now.strftime('%Y%m%d%H%M%S') + "-1"

      changeset_slug = values.description.to_url
      date_slug = now.strftime(liquibase.file_time_prefix_format)

      out_file = "#{date_slug}-#{changeset_slug}.xml"
      out_file = File.join(liquibase.changelog_dir, out_file)

      # Set ERB trim mode to omit blank lines ending in -%>
      erb = ERB.new(Liquibase.strip_heredoc(liquibase.template), nil, '-')

      fail("#{project.path_to(out_file)} exists already!") if File.exists?(out_file)

      File.open(out_file, 'w') do |f|
        f.write(erb.result(values.binding))
      end

      Liquibase.add_to_changelog(out_file, liquibase)
      info("Wrote #{project.path_to(out_file)}")
    end

    protected

    def associate_with(project)
      @project = project
    end
  end

  module ProjectExtension
    include Extension

    def liquibase
      @liquibase ||= Liquibase::Config.new(project)
    end

    # TODO: Get working with project coordinates?
    # E.g. buildr candlepin:server:changeset:blah
    # Something like /^(\w+:)*changeset:(.+)/ maybe and then grab the project from
    # the first capture group like Buildr.project("candlepin:server")
    CHANGESET_REGEX = /^changeset:(.+)/

    first_time do
      desc "Create a new Liquibase changeset."
      task('changeset', :description) do |task, args|
        if args[:description].nil?
          help = Liquibase.strip_heredoc(<<-HELP
          To create a changeset, add a colon to the `changeset` task followed
          by a description.  If your description has multiple words, remember to quote it
          so the shell doesn't break it up.

          For example:
              $ buildr "changeset:Create a new table"
          HELP
          )
          info(help)
        else
          ChangesetTask.run_local(args[:description])
        end
      end

      rule(CHANGESET_REGEX) do |task|
        description = CHANGESET_REGEX.match(task.name)[1]
        task('changeset').invoke(description)
      end
    end

    before_define do |project|
      changeset_task = ChangesetTask.define_task('changeset', :description)
      changeset_task.send(:associate_with, project)
    end
  end
end

class Buildr::Project
  include Liquibase::ProjectExtension
end
